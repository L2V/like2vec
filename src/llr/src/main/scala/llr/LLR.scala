package llr

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import java.util.Random
import scopt.OptionParser



/** Case class that defines all the Configurations
  *
  * master is a Spark, Mesos or YARN cluster URL, or a special “local” string to run in local mode
  * options Output options for LLR namely "default", MaxSale "max", Continuous Ratio "continuous"
  * useroritem Input option for LLR to find User-User "user" or Item-Item "item" Similarity
  * interactionsFile Input file containing user,items data
  * separator Delimiter used for separating user and item (eg. "," or "::")
  * maxInteractionsPerUserOrItem Specifies maximum number of Interactions between user and items
  * seed Specifies Seed parameter used in hash Function
  *
  */
case class Params(master: String = "local[4]",
                  options: String = "default",
                  useroritem: String = "user",
                  threshold: Double = 0.6,
                  interactionsFile:String = "s3://input",
                  outputFile: String ="s3://output",
                  separator:String = ",",
                  numSlices:Int = 2,
                  maxInteractionsPerUserOrItem:Int = 500, // we might want to include a min argument
                  seed:Int = 12345
                 ) extends AbstractParams[Params] with Serializable

/** Case class that defines an Interaction between a User and an Item to be used in log-likelihood
  * algorithm.
  *
  * @param user the User from the interaction input file
  * @param item the Item from the interaction input file
  *
  */
case class Interaction(val user: String, val item: String)

object LLR {

  /** Spark Application used as the first step in Like2Vec for generating the Log Likelihood Ratio for different
    * pairs of Users and sets of Items.
    *
    * Requires passing the output option as first argument and location of input file
    * that contains the relevant data as the second:
    *
    *    1. Different output options for configurations:
    * "default" for Default
    * "max" for MaxSale
    * "continuous" for Continuous Ratio
    *
    *    2. The Path for the Dataset of the form (User, Item)
    *
    */


  def main(args: Array[String]) {


    val defaultParams = Params()

    val parser = new OptionParser[Params]("llr_scala") {

      head("Main")
      opt[String]("master")
        .text(s"master: ${defaultParams.master}")
        .action((x, c) => c.copy(master = x))
      opt[String]("options")
        .text(s"options: ${defaultParams.options}")
        .action((x, c) => c.copy(options = x))
      opt[String]("useroritem")
        .text(s"useroritem: ${defaultParams.useroritem}")
        .action((x, c) => c.copy(useroritem = x))
      opt[Double]("threshold")
        .text(s"threshold: ${defaultParams.threshold}")
        .action((x, c) => c.copy(threshold = x))
      opt[String]("interactionsFile")
        .text(s"interactionsFile: ${defaultParams.interactionsFile}")
        .action((x, c) => c.copy(interactionsFile = x))
      opt[String]("outputFile")
        .text(s"outputFile: ${defaultParams.outputFile}")
        .action((x, c) => c.copy(outputFile = x))
      opt[String]("separator")
        .text(s"separator: ${defaultParams.separator}")
        .action((x, c) => c.copy(separator = x))
      opt[Int]("maxInteractionsPerUserOrItem")
        .text(s"maxInteractionsPerUserOrItem: ${defaultParams.maxInteractionsPerUserOrItem}")
        .action((x, c) => c.copy(maxInteractionsPerUserOrItem = x))
      opt[Int]("seed")
        .text(s"seed: ${defaultParams.seed}")
        .action((x, c) => c.copy(seed = x))

    }

    parser.parse(args, defaultParams).map { params =>
      userSimilarites(
        params.master,
        params.options,
        params.useroritem,
        params.threshold,
        params.interactionsFile,
        params.outputFile,
        params.separator,
        params.maxInteractionsPerUserOrItem,
        params.seed)
    }.getOrElse {
      sys.exit(1)
    }

  }

  /** Finds out the similarities amongst Users by first reading the data from the
    * User-Item interactions input file and then calls the loglikelihood method
    * to implement the algorithm.
    *
    * @param master is a Spark, Mesos or YARN cluster URL, or a special “local” string to run in local mode
    * @param options Output options for LLR namely "default", MaxSale "max", Continuous Ratio "continuous"
    * @param useroritem Input option for LLR to find User-User "user" or Item-Item "item" Similarity
    * @param interactionsFile Input file containing user,items data
    * @param separator Delimiter used for separating user and item (eg. "," or "::")
    * @param maxInteractionsPerUserOrItem Specifies maximum number of Interactions that can be taken
    * @param seed Specifies seed parameter of Hash Function used for randomization
    */

  def userSimilarites(
                       master:String,
                       options:String,
                       useroritem: String,
                       threshold: Double,
                       interactionsFile:String,
                       outputFile:String,
                       separator:String,
                       maxInteractionsPerUserOrItem:Int,
                       seed:Int){
    System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    System.setProperty("spark.locality.wait", "10000")

    val sc = new SparkContext(master,"LogLikelihoodRatio");


    /** Reading Data from input interactionsFile  */
    val rawInteractions = sc.textFile(interactionsFile)
    //    if file has a header:
    //    val header = rawInteractions.first()
    //    val rawInteractions_data = rawInteractions.filter(row => row != header)


    val rawInteractions_set =
      if (useroritem == "user")
        rawInteractions.map { line =>
          val fields = line.split(separator)
          Interaction(fields(0), fields(1))
        }
      else
        rawInteractions.map { line =>
          val fields = line.split(separator)
          Interaction(fields(1), fields(0))
        }


    /** interactions holds Number of Interactions of each item per user,
      * or Number of Interactions of each user per item
      *
      */

    val interactions =
      downSample(
        sc,
        rawInteractions_set,
        maxInteractionsPerUserOrItem,
        seed)

    interactions.cache()

    val numInteractions = interactions.count()

    val numInteractionsPerItem =
      countsToDict(interactions.map(interaction => (interaction.user, 1))
        .reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerItem)

    val numItems =
      countsToDict(interactions.map(interaction => (interaction.item, 1))
        .reduceByKey(_ + _))


    val cooccurrences = interactions.groupBy(_.item)
      .flatMap(
        { case (user, history) =>
         for (interactionA <- history;
               interactionB <- history)
          yield  ((interactionA.user, interactionB.user), 1l)
        }
      ).reduceByKey(_ + _)


    /** Determining the values of interaction of user/item A with user/item B.
      * Calls the loglikelihoodRatio method by using the above mentioned values to calculate
      * logLikelihood Similarity metric.
      *
      */

    val similarities = cooccurrences.map{
      case ((userA, userB), count) =>
        val interactionsWithAandB = count
        val interactionsWithAnotB = numInteractionsPerItem(userA) - interactionsWithAandB
        val interactionsWithBnotA = numInteractionsPerItem(userB) - interactionsWithAandB
        val interactionsWithNeitherAnorB =
          (numItems.size) - numInteractionsPerItem(userA) -
          numInteractionsPerItem(userB) + interactionsWithAandB
        val logLikelihood =
        LogLikelihood.logLikelihoodRatio(
          interactionsWithAandB,
          interactionsWithAnotB,
          interactionsWithBnotA,
          interactionsWithNeitherAnorB)
        val logLikelihoodSimilarity = 1.0 - 1.0 / (1.0 + logLikelihood)
      //((userA, userB), logLikelihoodSimilarity)

      /** Calculating Row Entropy and Column Entropy to give out different output options for
        * different configurations:
        *
        * "default" for Default
        * "max" for MaxSale
        * "continuous" for Continuous Ratio
        *
        */

      val rEntropy: Double = LogLikelihood.entropy(
        interactionsWithAandB+interactionsWithAnotB,interactionsWithBnotA+interactionsWithNeitherAnorB)
      val cEntropy: Double = LogLikelihood.entropy(
        interactionsWithAandB+interactionsWithBnotA,interactionsWithAnotB+interactionsWithNeitherAnorB)

      val llrD: Double = logLikelihoodSimilarity
      val llrM: Double = logLikelihoodSimilarity /(2.0 * math.max(rEntropy, cEntropy))
      val llrC: Double = logLikelihoodSimilarity / (1.0 + logLikelihoodSimilarity)

      //        if (options == "-d" && llrD > threshold){
      (userA, userB, llrD, llrM, llrC)
      //        }
      //        else if (options == "-m" && llrM > threshold) {
      //val outM = ((userA.toDouble, userB.toDouble), llrM)
      //        }
      //        else if (options == "-c" && llrC > threshold) {
      //val outC((userA.toDouble, userB.toDouble), llrC)
      //        }

    }


    if (options == "default") {
      similarities.map(x=>(x._1, x._2, x._3)).filter(f=>f._3 > threshold).repartition(1).saveAsTextFile(outputFile)
    }

    // note: there is no threshold filtering for "max" or "continuous"
    else if(options == "max"){
      similarities.map(x=>(x._1, x._2, x._4)).repartition(1).saveAsTextFile(outputFile)
    }
    else {
      similarities.map(x=>(x._1, x._2, x._5)).repartition(1).saveAsTextFile(outputFile)
    }

    sc.stop()

  } // end of userSimilarities


  /** Calculates the number of interactions of each user with every possible item
    * and the number of interactions of each item with every possible user by calling the countsToDict
    * method.
    *
    * @param sc Spark Context
    * @param interactions Contains a user and item pairs
    * @param maxInteractionsPerUserOrItem Specifies maximum number of Interactions that can be taken
    * @param seed Specifies seed parameter of Hash Function
    * @return Number of Interactions of each item per user, or Number of Interactions of each user per item
    */

  def downSample(sc:SparkContext, interactions: RDD[Interaction], maxInteractionsPerUserOrItem: Int,
                  seed: Int) = {

    val numInteractionsPerUser =
      countsToDict(interactions.map(interaction => (interaction.user, 1)).
        reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerUser)

    val numInteractionsPerItem =
      countsToDict(interactions.map(interaction => (interaction.item, 1)).
        reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerItem)

    /** Implements a hash function to generate a random number
      *
      * @param x Integer used as base of bit shifting operations
      * @return hashed value
      */

    def hash(x: Int): Int = {
      val r = x ^ (x >>> 20) ^ (x >>> 12)
      r ^ (r >>> 7) ^ (r >>> 4)
    }

    /** Applies the filtering on a per-partition basis to ensure repeatability in case of failures by
       incorporating the partition index into the random seed */

    interactions.mapPartitionsWithIndex({ case (index, interactions) => {
      val random = new Random(hash(seed ^ index))
      interactions.filter({ interaction => {
        val perUserSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerUser(interaction.user)) /
          numInteractionsPerUser(interaction.user)
        val perItemSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerItem(interaction.item)) /
          numInteractionsPerItem(interaction.item)
        random.nextDouble() <= math.min(perUserSampleRate, perItemSampleRate)
      }
      })
    }
    })
  } // end of downSample


  /** Counts the number of Interactions per user, and Interactions per item.
    *
    * @param tuples Key Value pair where key is either user or item and the value is its count
    *
    */

  def countsToDict(tuples: RDD[(String, Int)]) = {
    tuples.collect().foldLeft(Map[String, Int]()) {
      case (table, (item, count)) => table + (item -> count)
    }
  } // end of countToDict

} // end of LLR object


object LogLikelihood {

  /** Determines Log Likelihood Ratio by calculating row entropy, column entropy and matrix entropy using
    * following parameters.
    *
    * @param k11 Interactions of items With User A and User B, or users with Item A and Item B
    * @param k12 Interactions of items With User A and not User B, or users with Item A and not Item B
    * @param k21 Interactions of items With User B and not User A, or users with Item B and not Item A
    * @param k22 Interactions of items With neither User B nor User A, or users with neither Item B nor Item A
    * @return
    *
    */

  def logLikelihoodRatio(k11: Long, k12: Long, k21: Long, k22: Long) = {
    val rowEntropy: Double = entropy(k11 + k12, k21 + k22)
    val columnEntropy: Double = entropy(k11 + k21, k12 + k22)
    val matrixEntropy: Double = entropy(k11, k12, k21, k22)
    if (rowEntropy + columnEntropy < matrixEntropy) {
      0.0
    } else {
      2.0 * (rowEntropy + columnEntropy - matrixEntropy)
    }
  }


  /** Calculates x*log(x) expression
    *
    * @param x Any input of long datatype
    * @return number multiplied by its log or zero when number is zero
    */

  private def xLogX(x: Long): Double = {
    if (x == 0) {
      0.0
    } else {
      x * math.log(x)

    }
  }


  /**
    * Merely an optimization for the common two argument case of {@link #entropy(a: Long, b: Long)}
    * @see #logLikelihoodRatio(long, long, long, long)
    */

  private def entropy(a: Long, b: Long): Double = { xLogX(a + b) - xLogX(a) - xLogX(b) }

  /**
    * Calculates the unnormalized Shannon entropy.  This is
    *
    * -sum x_i log x_i / N = -N sum x_i/N log x_i/N
    *
    * where N = sum x_i
    *
    * If the x's sum to 1, then this is the same as the normal
    * expression.  Leaving this un-normalized makes working with
    * counts and computing the LLR easier.
    *
    * @return The entropy value for the elements
    */

  def entropy(elements: Long*): Double = {
    var sum: Long = 0
    var result: Double = 0.0
    for (element <- elements) {
      result += xLogX(element)
      sum += element
    }
    xLogX(sum) - result
  }
}