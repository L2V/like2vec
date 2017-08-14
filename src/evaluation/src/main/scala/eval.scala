import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by Dungeoun on 7/20/17.
  */
object eval {


  import java.io.Serializable

  import lib.AbstractParams
  import scopt.OptionParser


  /** Case class that defines all the Configurations
    *
    * @param master the User from the interaction input file
    *
    *
    */

  case class Params(master: String = "local",
                    options: String = "rmse",
                    //useroritem: String = "-u",
                    //threshold: Double = 0.6,
                    inputFile: String = "/Users/Dungeoun/Documents/SkymindLabsInternship/raghu/Like2Vec/evaluation/src/main/resources/pred_input/predictions-part-00000",
                    outputFile: String = "/Users/Dungeoun/Documents/SkymindLabsInternship/raghu/Like2Vec/evaluation/src/main/resources/pred_output/",
                    separator: String = ","
                    //numSlices: Int = 2,
                    //maxSimilarItemsperItem: Int = 100,
                    //maxInteractionsPerUserOrItem: Int = 500,
                    //seed: Int = 12345
                   ) extends AbstractParams[Params] with Serializable


  val hdfs = "hdfs://ip-172-31-23-118.us-west-2.compute.internal:8020"

  // val inputFile =hdfs+"/user/hadoop/trainset"
  def main(args: Array[String]) {


    val defaultParams = Params()

    val parser = new OptionParser[Params]("eval_scala") {

      head("Main")
      opt[String]("master")
        .text(s"master: ${defaultParams.master}")
        .action((x, c) => c.copy(master = x))
      opt[String]("options")
        .text(s"options: ${defaultParams.options}")
        .action((x, c) => c.copy(options = x))
//      opt[String]("useroritem")
//        .text(s"useroritem: ${defaultParams.useroritem}")
//        .action((x, c) => c.copy(useroritem = x))
//      opt[Double]("threshold")
//        .text(s"threshold: ${defaultParams.threshold}")
//        .action((x, c) => c.copy(threshold = x))
      opt[String]("inputFile")
        .text(s"inputFile: ${defaultParams.inputFile}")
        .action((x, c) => c.copy(inputFile = x))
      opt[String]("outputFile")
        .text(s"outputFile: ${defaultParams.outputFile}")
        .action((x, c) => c.copy(outputFile = x))
      opt[String]("separator")
        .text(s"separator: ${defaultParams.separator}")
        .action((x, c) => c.copy(separator = x))
//      opt[Int]("numSlices")
//        .text(s"numSlices: ${defaultParams.numSlices}")
//        .action((x, c) => c.copy(numSlices = x))
//      opt[Int]("maxSimilarItemsperItem")
//        .text(s"maxSimilarItemsperItem: ${defaultParams.maxSimilarItemsperItem}")
//        .action((x, c) => c.copy(maxSimilarItemsperItem = x))
//      opt[Int]("maxInteractionsPerUserOrItem")
//        .text(s"maxInteractionsPerUserOrItem: ${defaultParams.maxInteractionsPerUserOrItem}")
//        .action((x, c) => c.copy(maxInteractionsPerUserOrItem = x))
//      opt[Int]("seed")
//        .text(s"seed: ${defaultParams.seed}")
//        .action((x, c) => c.copy(seed = x))

    }

    parser.parse(args, defaultParams).map { params =>
      userSimilarites(
        params.master,
        params.options,
        params.inputFile,
        params.outputFile,
        params.separator)
    }.getOrElse {
      sys.exit(1)
    }

  }

  /** Finds out the similarities amongst Users by first reading the data from the
    * User-Item interactions input file and then calls the loglikelihood method
    * to implement the algorithm.
    *
    * @param master                       ? Not Used
    * @param options                      Output options for LLR namely Default -d, MaxSale -m, Continuous Ratio -c

    * @param inputFile             Input file containing user,items data
    * @param separator                    Delimiter used for separating user and item (eg. "," or "::")
    */

  def userSimilarites(
                       master: String,
                       options: String,
//                       useroritem: String,
//                       threshold: Double,
                       inputFile: String,
                       outputFile: String,
                       separator: String
//                       numSlices: Int,
//                       maxSimilarItemsperItem: Int,
//                       maxInteractionsPerUserOrItem: Int,
//                       seed: Int
                       ) {
    // System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    //  System.setProperty("spark.kryo.registrator", classOf[CoocRegistrator].getName)
    //  System.setProperty("spark.kryo.referenceTracking", "false")
    //   System.setProperty("spark.kryoserializer.buffer.mb", "8")
    System.setProperty("spark.locality.wait", "10000")
    //val sc = new SparkContext("local", "CooccurrenceAnalysis");

    val sc = new SparkContext(new SparkConf().setAppName("Evaluation"))//.setMaster(master))


    if (options == "rmse"){

      rmse.calcRMSE(inputFile, outputFile, sc)


    } else if (options == "map"){

      map.calcMAP(inputFile, outputFile, sc)



    } else if (options == "recallN"){


    } else if (options == "allMetrics"){

      rmse.calcRMSE(inputFile, outputFile, sc)

      map.calcMAP(inputFile, outputFile, sc)

      //sc.parallelize(Seq(rmseResult,mapResult)).saveAsTextFile(outputFile);



    }



  }
}
