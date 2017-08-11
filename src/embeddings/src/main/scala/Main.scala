import java.io.Serializable
import org.apache.spark.{SparkContext, SparkConf}
import scopt.OptionParser
import com.lib.AbstractParams
import org.apache.log4j.LogManager
import org.apache.log4j.Level
import org.apache.log4j.Logger

object Main {
  object Command extends Enumeration {
    type Command = Value
    val node2vec, randomwalk, embedding = Value
  }

  import Command._

  case class Params(iter: Int = 10,
                    lr: Double = 0.025,
                    numPartition: Int = 10,
                    dim: Int = 100,
                    window: Int = 10,
                    walkLength: Int = 80,
                    numWalks: Int = 10,
                    p: Double = 1.0,
                    q: Double = 1.0,
                    weighted: Boolean = true,
                    directed: Boolean = false,
                    degree: Int = 30,
                    indexed: Boolean = true,
                    nodePath: String = null,
                    input: String = null,
                    output: String = null,
                    cmd: Command = Command.node2vec) extends AbstractParams[Params] with Serializable
  val defaultParams = Params()

  val parser = new OptionParser[Params]("Node2Vec_Spark") {
    head("Main")
//    iter: Int = 10,
//    lr: Double = 0.025,
//    numPartition: Int = 10,
    opt[Int]("dim")
      .text(s"dim: ${defaultParams.dim}")
      .action((x, c) => c.copy(dim = x))
    opt[Int]("window")
      .text(s"window: ${defaultParams.window}")
      .action((x, c) => c.copy(window = x))
    opt[Int]("walkLength")
      .text(s"walkLength: ${defaultParams.walkLength}")
      .action((x, c) => c.copy(walkLength = x))
    opt[Int]("numWalks")
      .text(s"numWalks: ${defaultParams.numWalks}")
      .action((x, c) => c.copy(numWalks = x))
    opt[Double]("p")
      .text(s"return parameter p: ${defaultParams.p}")
      .action((x, c) => c.copy(p = x))
    opt[Double]("q")
      .text(s"in-out parameter q: ${defaultParams.q}")
      .action((x, c) => c.copy(q = x))
    opt[Boolean]("weighted")
      .text(s"weighted: ${defaultParams.weighted}")
      .action((x, c) => c.copy(weighted = x))
    opt[Boolean]("directed")
      .text(s"directed: ${defaultParams.directed}")
      .action((x, c) => c.copy(directed = x))
    opt[Int]("degree")
      .text(s"degree: ${defaultParams.degree}")
      .action((x, c) => c.copy(degree = x))
    opt[Boolean]("indexed")
      .text(s"Whether nodes are indexed or not: ${defaultParams.indexed}")
      .action((x, c) => c.copy(indexed = x))
    opt[String]("nodePath")
      .text("Input node2index file path: empty")
      .action((x, c) => c.copy(nodePath = x))
    opt[String]("input")
      .required()
      .text("Input edge file path: empty")
      .action((x, c) => c.copy(input = x))
    opt[String]("output")
      .required()
      .text("Output path: empty")
      .action((x, c) => c.copy(output = x))
    opt[String]("cmd")
      .required()
      .text(s"command: ${defaultParams.cmd.toString}")
      .action((x, c) => c.copy(cmd = Command.withName(x)))
    note(
      """
        |For example, the following command runs this app on a synthetic dataset:
        |
        | bin/spark-submit --class Main \
      """.stripMargin +
        s"|   --lr ${defaultParams.lr}" +
        s"|   --walkLength ${defaultParams.walkLength}" +
        s"|   --iter ${defaultParams.iter}" +
        s"|   --numPartition ${defaultParams.numPartition}" +
        s"|   --dim ${defaultParams.dim}" +
        s"|   --window ${defaultParams.window}" +
        s"|   --input <path>" +
        s"|   --node <nodeFilePath>" +
        s"|   --output <path>"
    )
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, defaultParams).map { param =>
//      val conf = new SparkConf()
//        .setMaster("local[*]")
//        .set("spark.executor.memory", "4g")
//      val context: SparkContext = new SparkContext(conf)

      val context: SparkContext = new SparkContext(new SparkConf().setAppName("Node2Vec"))

// To test locally
//      val context: SparkContext = new SparkContext(new SparkConf().setMaster("local[*]").setAppName("Node2Vec"))

      //      val sc = new SparkContext(new SparkConf().setAppName("CooccurrenceAnalysis"));

      Node2vec.setup(context, param)

      param.cmd match {
        case Command.node2vec => Node2vec.load()
          .initTransitionProb()
          .randomWalk()
          .embedding()
          .save()
        case Command.randomwalk => Node2vec.load()
          .initTransitionProb()
          .randomWalk()
          .saveRandomPath()
        case Command.embedding => {
          val randomPaths = Word2vec.setup(context, param).read(param.input)
          Word2vec.fit(randomPaths).save(param.output)
          Node2vec.loadNode2Id(param.nodePath).saveVectors()
        }
      }
    } getOrElse {
      sys.exit(1)
    }
  }
}