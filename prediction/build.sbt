scalaVersion := "2.11.1"

resolvers++=Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases" at "http://oss.sonatype.org/service/local/staging/deploy/maven2/")

//assemblyOutputPath in assembly := file("/Users/jaimealmeida/Repos/Like2Vec/LSHandRMSE/target/scala-2.10/Prediction6.jar")

mainClass in assembly := Some("Prediction")

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value


// new config
lazy val root = (project in file(".")).settings(
  name := "prediction",
  version := "2.1",
  scalaVersion := "2.11.1",
  libraryDependencies ++= Seq(
    "org.apache.spark" %% "spark-core" % "2.1.0" % "provided",
    "org.apache.spark" %% "spark-mllib"  % "2.1.0" % "provided",
    "org.scala-lang" % "scala-compiler" %  scalaVersion.value, // "2.10.0"
    "com.github.scopt" %% "scopt" % "3.3.0" //,  % "provided",
    //    "com.github.karlhigley" % "spark-neighbors_2.10" % "0.2.2" % "provided"
  )
  ,
  mainClass in assembly := Some("Prediction")

)

assemblyExcludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  val excludes = Set(
    "jsp-api-2.1-6.1.14.jar",
    "jsp-2.1-6.1.14.jar",
    "jasper-compiler-5.5.12.jar",
    "commons-beanutils-core-1.8.0.jar",
    "commons-beanutils-1.7.0.jar",
    "servlet-api-2.5-20081211.jar",
    "servlet-api-2.5.jar"
  )
  cp filter { jar => excludes(jar.data.getName) }
}

assemblyMergeStrategy in assembly := {
  // case "project.clj" => MergeStrategy.discard // Leiningen build files
  case x if x.endsWith(".class") => MergeStrategy.last
  case x if x.startsWith("META-INF") => MergeStrategy.discard // Bumf
  case x if x.endsWith(".html") => MergeStrategy.discard // More bumf
  //  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last // For Log$Logger.class
  //        case x => old(x)
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}