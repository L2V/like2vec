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
        (fields.head.toLong, tail)
    }

  }

  /** Wrapper around 'Spark Neighbors' package 'neighbors' function that reads embeddings and formats them into vectors
    * prior to calculating its 'no_neighbors' approximate neighbors by applying a collision strategy to the hash tables
    * and then computing the actual distance between candidate pairs.
    *
    * @param file File containing embeddings from 'network-embedding' module
    * @param dims dimension of hash tables
    * @param tables number of tables
    * @param no_neighbors Implicit Spark Context
    * @param signature signature length for each data point
    * @param sc Implicit Spark Context
    * @return RDD containing an Array of (User, List(Neighbors, distance) for each user in train set
    */

  def getNeighbors(file: String, dims: Int, tables: Int, signature: Int, no_neighbors: Int)(implicit sc: SparkContext): RDD[(Long, List[(Long, Double)])] = {

      val embeedings_data = sc.textFile(file)

      // header needed to be removed in previous embedding format
      // val header = inputRDD.first()
      // val embeedings_data = inputRDD.filter(row => row != header)
      // inputRDD.collect().foreach(println)

      val points = getEmbeddingsinLSHFormat(embeedings_data)
      val ann =
        new ANN(dims, "cosine")
          .setTables(tables)
          .setSignatureLength(signature)

      val model1 = ann.train(points)

      val nn = model1.neighbors(no_neighbors).map {
        case (user, neighborsList) =>
          (user, neighborsList.toList) }
      nn

    }

  /** Wrapper around 'Spark Neighbors' package 'neighbors' function that reads embeddings and formats them into vectors
    * prior to calculating its 'no_neighbors' approximate neighbors by applying a collision strategy to the hash tables
    * and then computing the actual distance between candidate pairs.
    *
    * @param file File containing embeddings from 'network-embedding' module
    * @param test_data contains 'users' and 'items'.
    * @param neighbors numbers of neighbors needed per user
    * @param sc Implicit Spark Context
    * @return RDD containing an Array of (User, List(Neighbors, distance) for each user in train set
    */

  def getExactNeighbors(file: String, test_data: String, neighbors: Int)(implicit sc: SparkContext): RDD[(Long, List[(Long, Double)])] = {

    // TODO: pass 'type of average' as argument in signature
    val typeAvg = 0 // '0' is to calculate weighted average and '1' naive

    val embedding_data = sc.textFile(file)
    val points = getEmbeddingsinLSHFormat(embedding_data)

    // filter out pairs not in test set
    val usersTest = getTestData(test_data)
    val justUsersTest = usersTest.map(_._1).distinct().collect()

    // each embedding in 'points' is in format (id, sparsevector embedding)
    val filteredPoints = points.filter { case (a, b) => justUsersTest.contains(a)}

    // pair-wise comparison
    val pairs = filteredPoints.cartesian(points)
    // filter out own points from cartesian so distance to self is not calculated
    val pairsFiltered = pairs.filter{case (a,b) => a != b }

    val dist = pairsFiltered.map { case (x, y) => ((x._1,y._1), compute(x._2, y._2))}

    val n = dist.map {
        case ((id1:Long, id2:Long), dist:Double)
        => List((id1, (id2, dist)), (id2, (id1, dist)))
    }

    val simplified = n.flatMap {
      case (both) => both.map( a => a)
    }

    val simplifiedN = simplified.groupByKey.mapValues(_.toList) //reduceByKey()

    // top by key
    val sortedNn = simplifiedN.map( x => (x._1, x._2.sortBy(-_._2).distinct.take(neighbors)))

    sortedNn
  }


  /** Computes cosine similarity between two embeddings
    * @param v1 Embedding of user 1.
    * @param v2 Embedding of user 2.
    * @return cosine similarity between v1 and v2
    */
  def compute(v1: SparseVector, v2: SparseVector): Double = {
    val dotProduct = LinalgShim.dot(v1, v2)
    val norms = Vectors.norm(v1, 2) * Vectors.norm(v2, 2)
    1.0 - (math.abs(dotProduct) / norms)
  }

  /** Computes cosine similarity between two embeddings
    * @param input RDD containing embedding features separated by a space.
    * @return RDD of tuples containing (User, SparseVector of embedding)
    */
  def getEmbeddingsinLSHFormat(input: RDD[String]): RDD[(Long, SparseVector)] = {
    input.map {
      line =>
        val fields = line.split(" ")
        val tail = fields.tail.map(x => x.toDouble)
        val sparseVectorEmbeddings = Vectors.dense(tail).toSparse
        (fields.head.toLong, sparseVectorEmbeddings)
      }

  }


  /** Formats training data and loads it into an RDD
    * @param fileName RDD containing triplet (user, item, rating)
    * @param sc Implicit Spark Context
    * @return RDD of tuples containing (Item, List(User, Rating))
    */
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


  /** Formats test data and loads it into an RDD
    * @param fileName RDD containing triplet (user, item, rating)
    * @param sc Implicit Spark Context
    * @return RDD of tuples containing (Item, List(User, Rating))
    */
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

} // end of Loaders object
