name := "n2v"

version := "3.8"

scalaVersion := "2.11.1"

//assemblyOutputPath in assembly := file("/Users/jaimealmeida/Repos/Like2Vec/n2v2.jar")


libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.apache.spark" %% "spark-core" % "2.1.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.1.0"  % "provided",
  "com.github.scopt" %% "scopt" % "3.5.0"
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
  case x if x.startsWith("META-INF") => MergeStrategy.discard // Bumf
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
