import org.apache.spark.SparkContext

/**
  * Created by Dungeoun on 7/20/17.
  */
object map {

  def calcMAP(inputFile: String, outputFile: String, sc: SparkContext) {

    //val sc = new SparkContext("local","MeanAveragePrecision");

    //val sc = new SparkContext(new SparkConf().setAppName("MeanAveragePrecision").setMaster("local[*]").set("spark.driver.allowMultipleContexts", "true"))

    val actualRatings = sc.textFile(inputFile).map { line =>

      val data =line.replace("(", "").replace(")", "").split(",")
      val user= data(0).toLong
      val movie = data(1).toLong
      val actualRating= data(2).toDouble
      val predictedRating=data(3).toDouble
      (user,movie,actualRating)
    }.sortBy(f=>f._3,ascending=false)


    val predictedRatings = sc.textFile(inputFile).map{
      line =>
        val data =line.replace("(", "").replace(")", "").split(",")
        val user= data(0).toLong
        val movie = data(1).toLong
        val actualRating= data(2).toDouble
        val predictedRating=data(3).toDouble
        (user,movie,predictedRating)
    }.sortBy(f=>f._1, ascending=true,1)

    val predictedUser = predictedRatings.map{case(user,movie,rating)=> (user,movie)}.groupByKey().map( f=>f._1)

    val actualMovies = actualRatings.filter(f=>f._3 > 3.0).map{case(user,movie,rating)=> (user,movie)}.groupByKey().collectAsMap()

    val actualMoviesKeySet =actualMovies.keySet
    //filter(f=>f._3 > 3.0)
    val predictedMovies =predictedRatings.map{case(user,movie,rating)=> (user,(movie,rating))}.groupByKey().collectAsMap()

    val averagePrecision =predictedUser.filter(f=>actualMovies.contains(f)).map{ case(user) =>

      val actualMoviesforAP =actualMovies.apply(user).toList


      val predictedMoviesforAP = predictedMovies.apply(user).toList.sortBy(f=>f._2).reverse.map{case(movie,rating) => movie }.take(10)



      (avgPrecisionK(actualMoviesforAP,predictedMoviesforAP,10))

    }



    //averagePrecision.saveAsTextFile("/Users/Dungeoun/Documents/SkymindLabsInternship/raghu/Like2Vec/evaluation/src/main/resources/mapOutput")

    val mapK =   (averagePrecision.reduce(_+_) /averagePrecision.count())

    //println(mapK)

    sc.parallelize(Seq(mapK)).coalesce(1).saveAsTextFile(outputFile+"/map");






  }

  def avgPrecisionK(actual: List[Long], predicted: List[Long], k: Int):
  Double = {
    // val predK = predicted.take(k)

    //actual.foreach(println)

    //predicted.foreach(println)

    var score = 0.0
    var numHits = 0.0
    for ((p, i) <- predicted.zipWithIndex) {
      if (actual.contains(p)) {
        numHits = numHits+ 1.0
        score = score + numHits / (i.toDouble + 1.0)
      } }
    if (actual.isEmpty) {
      1.0

    } else {

      score / scala.math.min(actual.size, k).toDouble

    } }



}
