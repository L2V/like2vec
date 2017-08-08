package com.utilities

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.Vectors
import com.github.karlhigley.spark.neighbors.ANN
import breeze.linalg.norm
import org.apache.spark.mllib.linalg.LinalgShim
import org.apache.spark.mllib.rdd.MLPairRDDFunctions._
import scala.collection.Iterator

/**
  * Definition of functions to load data
  */
object Loaders {


  def getEmbeddings(file: String)(implicit sc: SparkContext): RDD[(Long, Array[Double])] = {

    val inputRDD = sc.textFile(file)
    val header = inputRDD.first()
    val embeddingsData = inputRDD.filter(row => row != header)

    embeddingsData.map {
      line =>
        val fields = line.split(" ")
        val tail = fields.tail.map(x => x.toDouble)
//        val sparseVectorEmbeddings = Vectors.dense(tail).toSparse

        (fields.head.toLong, tail)
    }

  }

    def getNeighbors(file: String, dims: Int, tables: Int, signature: Int, no_neighbors: Int)(implicit sc: SparkContext): RDD[(Long, List[(Long, Double)])] = {

      val inputRDD = sc.textFile(file)
      val header = inputRDD.first()
      val embeedings_data = inputRDD.filter(row => row != header)
      // inputRDD.collect().foreach(println)
      val points = getEmbeddingsinLSHFormat(embeedings_data)
      val ann =
        new ANN(dims, "cosine")
          .setTables(tables)
          .setSignatureLength(signature)

      val model1 = ann.train(points)

      val nn = model1.neighbors(no_neighbors).map { case (user, neighborsList) => (user, neighborsList.toList) }
      //, neighborsList.map(_._2).reduce((acc, elem) => (acc + elem))
      nn

    }

  def getExactNeighbors(file: String, test_data: String, neighbors: Int)(implicit sc: SparkContext): RDD[(Long, List[(Long, Double)])] = {

    // pass these arguments in signature
    val typeAvg = 0 // this relates to weighted average and naive and needs to be part of prediction

    val embedding_data = sc.textFile(file)
    val points = getEmbeddingsinLSHFormat(embedding_data)

    // filter out pairs not in test set
    val usersTest = getTestData(test_data)
    val justUsersTest = usersTest.map(_._1).distinct().collect()

    // each embedding in points is in format (id, sparsevector embedding)
    val filteredPoints = points.filter { case (a, b) => justUsersTest.contains(a)}


    val pairs = filteredPoints.cartesian(points) // filtered pair-wise comparison

    // need to filter out own points from cartesian so distance to self is not calculated
    val pairsFiltered = pairs.filter{case (a,b) => a != b }


    val dist = pairsFiltered.map { case (x, y) => ((x._1,y._1), compute(x._2, y._2))}

//    val nnFormating = dist
//      .reduceByKey((a, b) => a)

    val n = dist.map {
        case ((id1:Long, id2:Long), dist:Double)
        => List((id1, (id2, dist)), (id2, (id1, dist)))
    }

    val simplified = n.flatMap {
      case (both) => both.map( a => a)
    }

    //.reduce(case ((x,y),(x2,y2)) => (x, y)) //( case (a,b) => a)
//    val simplified = n.flatMap()

//    val r = n.topByKey(numMovies)

    // review
    val simplifiedN = simplified.groupByKey.mapValues(_.toList) //reduceByKey()

//    val r = simplifiedN.map{
//      case (user, neighborList) =>
//        (user, neighborList.toList)
    val oneOfThem = simplifiedN.take(1)
    val nn = oneOfThem(0)._2

    // top by key
    val sortedNn = simplifiedN.map( x => (x._1, x._2.sortBy(-_._2).toSet.toList.take(neighbors)))
//    val sortedNnexample = sortedNn.take(1)
//    println(sortedNnexample(0)._2)
    sortedNn
  }


//    val nn = nnFormating.neighbors(10).map { case (user, neighborList) => (user, neighborList.toList)}



  def compute(v1: SparseVector, v2: SparseVector): Double = {
    val dotProduct = LinalgShim.dot(v1, v2)
    val norms = Vectors.norm(v1, 2) * Vectors.norm(v2, 2)
    1.0 - (math.abs(dotProduct) / norms)
  }

    def getEmbeddingsinLSHFormat(input: RDD[String]): RDD[(Long, SparseVector)] = {
      input.map {
        line =>
          val fields = line.split(" ")
          val tail = fields.tail.map(x => x.toDouble)
          val sparseVectorEmbeddings = Vectors.dense(tail).toSparse


          (fields.head.toLong, sparseVectorEmbeddings)
      }

    }

    def getTrainData(fileName: String)(implicit sc: SparkContext): RDD[(Int, List[(Long, Double)])] = {

      val trainData = sc.textFile(fileName).map { line =>
        val train = line.split(",")
        val user = train(0)
        val item = train(1)
        val rating = train(2)

        (item.toInt, List((user.toLong, rating.toDouble)))

      }

      val formatted = trainData.reduceByKey(_ ++ _)
      formatted
    }

  def getTestData(fileName: String)(implicit sc: SparkContext): RDD[(Long, List[(Int, Double)])] = {

    val trainData = sc.textFile(fileName).filter(!_.isEmpty()).map { line =>
      val train = line.split(",")
      val user = train(0)
      val item = train(1)
      val rating = train(2)
// this is done so we can filter  USERS present in testset
      (user.toLong, List((item.toInt, rating.toDouble)))

    }

    val formatted = trainData.reduceByKey(_ ++ _)
    formatted


  }


}
