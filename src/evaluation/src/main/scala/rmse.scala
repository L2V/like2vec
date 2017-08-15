import org.apache.spark.SparkContext



/**
  * Created by Dungeoun on 7/20/17.
  */
object rmse {


    def calcRMSE(testFile: String, outputFile: String, sc: SparkContext ) {

      //.set("spark.driver.allowMultipleContexts", "true"))
      //val sc = new SparkContext("local","RMSE")

      val predictions = sc.textFile(testFile).filter(!_.isEmpty()).map { line =>
        val test = line.replace("(","").replace(")","").split(",")
        val testUser = test(0).toLong
        val testMovie = test(1).toInt
        val testRating = test(2).toDouble
        val predictedRate = test(3).toDouble
        (testUser, testMovie, testRating, predictedRate)
      }

      val neighborPredictions = predictions.map {
        case (testUser, testMovie, testRating, predictedRate) =>


          val errorCalc = math.pow((testRating - predictedRate), 2)

          (testUser, testMovie, testRating, predictedRate, errorCalc)




      }
      val denom = neighborPredictions.map(_._5).count()
      val numerator = neighborPredictions.map(_._5).reduce((acc, elem) => (acc + elem))

      val rmseValue = Math.sqrt(numerator / denom)


      sc.parallelize(Seq(rmseValue)).coalesce(1).saveAsTextFile(outputFile+"/rmse")




    }



}
