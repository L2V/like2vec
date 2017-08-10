import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import scopt.OptionParser
import com.lib.AbstractParams
import com.utilities.Loaders.{ getExactNeighbors, getNeighbors, getTrainData}


/**
  * Created by jaimealmeida on 7/13/17.
  */
object Prediction {

  /** Spark Application used as the third step in Like2Vec for generating predictions given User and Item inputs
    *
    *  LLR -> Embeddings -> Prediction -> Evaluation
    *
    * Requires passing the type of neighborhood calculation
    *
    * Type of Neighborhood calculation:
    * "ntype" - type of nearest neighbor calculation type: 'KNN' or 'LSH'
    *     If "ntype" is equal to 'KNN'
    *       "neighbors" - number of neighbors to be used for prediction calculation
    *     If "ntype" is equal to 'LSH'
    *       "dim" - dimension for ANN model
    *       "tables" - number of tables
    *       "signature" - signature size of hashing function
    *       "neighbors" - number of neighbors to be used for prediction calculation
    *
    * File paths:
    *   Inputs:
    *     "train" - File path to train set
    *     "test" - File path to test set
    *     "embedding" - File path to embeddings
    *
    *    Outputs:
    *     "predictions" - File path for folder to save predictions
    *
    */


  case class Params(dim: Int = 100,
                    tables: Int = 1,
                    signature: Int = 16,
                    neighbors: Int = 100,
                    ntype: String = "KNN",
                    train: String = null,
                    test: String = null,
                    embeddings: String = null,
//                    rmse: String = null,
                    predictions: String = null) extends AbstractParams[Params] with Serializable

  val defaultParams = Params()

  val parser = new OptionParser[Params]("LSH_Spark") {
    head("Main")
    opt[Int]("dim")
      .text(s"dimension for ANN model: ${defaultParams.dim}")
      .action((x, c) => c.copy(dim = x))
    opt[Int]("tables")
      .text(s"set tables: ${defaultParams.tables}")
      .action((x, c) => c.copy(tables = x))
    opt[Int]("signature")
      .text(s"Signature size: ${defaultParams.signature}")
      .action((x, c) => c.copy(signature = x))
    opt[Int]("neighbors")
      .text(s"Number of neighbors: ${defaultParams.neighbors}")
      .action((x, c) => c.copy(neighbors = x))
    opt[String]("ntype")
      .required()
      .text("Input nearest neighbor calculation type: 'KNN' or 'LSH'")
      .action((x, c) => c.copy(ntype = x))
    opt[String]("train")
      .required()
      .text("Input train set path: empty")
      .action((x, c) => c.copy(train = x))
    opt[String]("test")
      .required()
      .text("Input test set path: empty")
      .action((x, c) => c.copy(test = x))
    opt[String]("embedding")
      .required()
      .text("Embeddings path: empty")
      .action((x, c) => c.copy(embeddings = x))
//    opt[String]("rmse")
//      .required()
//      .text("RMSE output path: empty")
//      .action((x, c) => c.copy(rmse = x))
    opt[String]("predictions")
      .required()
      .text("Predictions output path: empty")
      .action((x, c) => c.copy(predictions = x))
    note(
      """
        | Run predictions with:
        |
        | bin/spark-submit --class Main [parameters]
      """.stripMargin
    )
  }

  def main(args: Array[String]) {

    val sc = new SparkContext(new SparkConf().setAppName("Prediction")) // .setMaster("local[*]")

    parser.parse(args, defaultParams).map{
      param =>
        val trainData = getTrainData(param.train)(sc)

        // Determine type of  neighbors calculation - "LSH" or "NN"
        val neighborType = param.ntype

        // Nearest Neighbors calculation
        if (neighborType == "LSH") {
          val neighbors = getNeighbors(param.embeddings, param.dim, param.tables, param.signature, param.neighbors)(sc)

          // Calculate prediction with Aproximate Nearest Neighbors
          val predictions = predictionCalculation(trainData, neighbors, param.test)(sc)
          // Save prediction in one text file
          predictions.coalesce(1).saveAsTextFile(param.predictions)
        }
        else {
          val exactNeighbors = getExactNeighbors(param.embeddings, param.test, param.neighbors)(sc)

          // Calculate prediction with Exact Nearest Neighbors
          val predictions = predictionCalculation(trainData, exactNeighbors, param.test)(sc)
          // Save prediction in one text file
          predictions.coalesce(1).saveAsTextFile(param.predictions)
        }
    }.getOrElse {
      sys.exit(1)
    }
  } // end of main


  /** Calculates the predicted rating for each of the User/Item pairs in the test set
    * method.
    *
    * @param trainData data used for training. Train data is used access ratings of neighbors
    * @param neighbors RDD containing a tuple made out of each User in train set and a list of K tuples (Neighbor, Neighbor's
    *                  distance)
    * @param testFile Contains list of user, item pairs to be predicted
    * @param sc Implicit Spark Context
    * @return Tuple containing (testUser, testMovie, testRating, predictedRate) for each entry in testFile
    */

  def predictionCalculation(trainData: RDD[(Int, List[(Long, Double)])], neighbors: RDD[(Long, List[(Long, Double)])], testFile: String)(implicit sc: SparkContext) = {

    // Load train data
    val trainDataAsMap = trainData.collectAsMap()
    val neighborDataAsMap = neighbors.collectAsMap()

    // Load neighbors data
    val neighborUserKeys = neighbors.collectAsMap().keySet
    val trainDataMovieKeys = trainDataAsMap.keySet

    // Load test file and format it as (testUser, testMovie, testRating)
    val predictions = sc.textFile(testFile).filter(!_.isEmpty()).map { line =>
      val test = line.split(",")
      val testUser = test(0).toLong
      val testMovie = test(1).toInt
      val testRating = test(2).toDouble
      (testUser, testMovie, testRating)
    }

    val neighborPredictions = predictions.filter(
      // filters out predictions for which neighbors have not rated items
      // and items that are not in the training set
      f => neighborUserKeys.contains(f._1) && trainDataMovieKeys.contains(f._2)).map {
      case (testUser, testMovie, testRating) =>
        val trainuser = trainDataAsMap.apply(testMovie).toMap
        val neighborUser = neighborDataAsMap.apply(testUser).toMap
        val userweight = trainuser.keySet.intersect(neighborUser.keySet).map {
          // TODO: this is an area that can be improved if we filter neighbors prior to calculation
          f => (f, trainuser.get(f).getOrElse(0).asInstanceOf[Double], neighborUser.get(f).getOrElse(0).asInstanceOf[Double])
        }.toList

        val totalDistance = userweight.map(_._3).sum
        val predictedRate = userweight.map {
          case (user, rating, distance) => ((distance / totalDistance) * rating)
        }.sum
        (testUser, testMovie, testRating, predictedRate)
    }
    neighborPredictions


  }


  /** Calculates rating prediction using weighted average of each neighbors rating divided by similarity
    *
    * @param topItems List containing top K tuples (user rating, distance)
    * @return average prediction
    * */

  def weightedAverage(topItems: List[(Double, Double)]): Double = {

    val (num, denom) = topItems.foldLeft((0.0, 0.0)) { (acc, entry) =>
      val expWeight = math.exp(entry._1)
      val sumWeightedRatings = acc._1 + (expWeight * entry._2)
      val sumOfDistances = acc._2 + expWeight
      (sumWeightedRatings, sumOfDistances)
    }
    num / denom
  }


  /** Calculates rating prediction using average of neighbors rating divided by number of neighbors
    *
    * @param topItems List containing top K tuples (user rating, distance)
    * @return average prediction
    */

  def naiveAverage(topItems: List[(Double, Double)]): Double = {
    val numMovies = 10

    topItems.map(_._2).sum / numMovies
  }

}
