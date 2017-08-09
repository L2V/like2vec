//resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")
//addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")
//addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.4")
//mainClass in assembly := Some("llr.LLR")


//assemblyExcludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
//  val excludes = Set(
//    "junit-4.5.jar", // We shouldn't need JUnit
//    "javax.inject-2.4.0-b34.jar:", // addded from curren project
//    "jsp-api-2.1-6.1.14.jar",
//    "jsp-2.1-6.1.14.jar",
//    "jasper-compiler-5.5.12.jar",
//    "minlog-1.2.jar", // Otherwise causes conflicts with Kyro (which bundles it)
////    "janino-2.5.16.jar", // Janino includes a broken signature, and is not needed anyway
//    "janino-3.0.0.jar",
//    "commons-beanutils-core-1.8.0.jar", // Clash with each other and with commons-collections
//    "commons-beanutils-1.7.0.jar",      // "
//    "hadoop-core-1.0.3.jar", // Brought in via dfs-datastores-cascading-1.3.4
//    "protobuf-java-2.4.1.jar" // Hadoop needs 2.5.0
//  )
//  cp filter { jar => excludes(jar.data.getName) }
//}