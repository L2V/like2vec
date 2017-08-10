

name := "evaluation"

version := "1.1"

scalaVersion := "2.11.11"

resolvers++=Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases" at "http://oss.sonatype.org/service/local/staging/deploy/maven2/")


libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "2.1.1"

libraryDependencies += "org.apache.spark" % "spark-mllib_2.11" % "2.1.1" % "provided"

//libraryDependencies += "org.apache.spark" %% "spark-mllib_2.10" % "2.1.0"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"


// grading libraries
libraryDependencies += "junit" % "junit" % "4.10" % "test"
//libraryDependencies ++= assignmentsMap.value.values.flatMap(_.dependencies).toSeq


resolvers += Resolver.sonatypeRepo("public")



assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
  case PathList("reference.conf") => MergeStrategy.concat
}

