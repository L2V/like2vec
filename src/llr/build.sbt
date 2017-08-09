name := "llr"

version := "1.2"

//scalaVersion := "2.10.6"
scalaVersion := "2.11.1"


libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.1.0", // % "provided",
  "org.scala-lang" % "scala-compiler" %  scalaVersion.value, // "2.10.0"
  "com.github.scopt" %% "scopt" % "3.3.0" //,  % "provided",
)


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
