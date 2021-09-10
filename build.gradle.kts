import de.itemis.mps.gradle.BuildLanguages
import de.itemis.mps.gradle.RunAntScript
import de.itemis.mps.gradle.GitBasedVersioning
import de.itemis.mps.gradle.*
import org.gradle.api.DefaultTask
import org.gradle.internal.os.OperatingSystem
import java.time.LocalDate
import java.time.temporal.WeekFields

buildscript {
  dependencies {
    classpath("de.itemis.mps:mps-gradle-plugin:1.4.+")
  }
}

// Credentials for github - allows to obtain packages
var githubUsername:String? = null
var githubToken:String? = null

if(System.getenv("GITHUB_ACTOR")!=null){
    //CI build uses github built-in credentials
    githubUsername = System.getenv("GITHUB_ACTOR")
    githubToken = System.getenv("GITHUB_TOKEN")
}else if(project.hasProperty("github_username")){
    //local build uses user provided credentials from gradle.properties file
    githubUsername = project.properties["github_username"] as String
    githubToken = project.properties["github_token"] as String
}else{
}

// Repository declarations
repositories {
    // maven {url = uri("https://projects.itemis.de/nexus/content/repositories/mbeddr")}
    maven {url = uri("https://maven.pkg.github.com//mbeddr/mbeddr.core")
        credentials {
            username = githubUsername
            password = githubToken
        }
    }
    maven {url = uri("https://maven.pkg.github.com//mbeddr/build.publish.jdk")
        credentials {
            username = githubUsername
            password = githubToken
        }
    }
    maven {url = uri("https://maven.pkg.github.com/IETS3/iets3.opensource")
        credentials {
            username = githubUsername
            password = githubToken
        }
    }

    mavenCentral()
    gradlePluginPortal()
}

println("Github user name: " + githubUsername)


//Plugin declarations
plugins {
    base
    `maven-publish`
    id("download-jbr") version "1.4.242.6545d9f"
    id("modelcheck") version "1.4.229.b4b86d9"
    id("java-library")
}

downloadJbr {
    jbrVersion = "11_0_10-b1145.96"
    downloadDir = file("jbrdl")
}

// Detect and verify JDK location
var jdk_home = "./jbrdl/jbr/"
if (JavaVersion.current() != JavaVersion.VERSION_11) {
    throw GradleException("This build script requires JBR Java 11 but you are currently using ${JavaVersion.current()}.\n")
    if (!File(jdk_home, "lib").exists()) {
      throw GradleException("Unable to locate JDK home folder. Detected folder is: $jdk_home")
    }
}

logger.info("Using JDK at {}", jdk_home)
ext.set("jdk_home", jdk_home)

// Variable declarations
val majorVersion = 2020
val minorVersion = 3
val patchVersion = 5
val mpsVersion = "$majorVersion.$minorVersion.$patchVersion"
var rcpVersion = "$majorVersion.$minorVersion-SNAPSHOT"

val releaseBuild = System.getProperty("releaseBuild") != null && System.getProperty("releaseBuild") == "true"
println("releaseBuild: $releaseBuild \n")

group = "libre.doge.gradlegithubactions"
description = "libre.doge.test tests tests :>"

val mps = configurations.create("mps")
val junitAnt = configurations.create("junitAnt")
val iets3 = configurations.create("iets3")
val mbeddr = configurations.create("mbeddr")
val jdk = configurations.create("jdk")

val mpsHomeDir = file(project.findProperty("mpsHomeDir") ?: "$projectDir/build/mps")
val mpsProjectDir = file("$projectDir/code/libre.doge.gradlegithubactions")
val artifactsDir = file("$projectDir/build/artifacts")
val buildDir = "$projectDir/build"
val jdkDir = file("$artifactsDir/jdk")
val previousVersionDir = file("$artifactsDir/previousVersion")
val currentVersionDir = file("$artifactsDir/libre.doge.ditrso")

ant.properties["libre.doge.gradlegithubactions.git.home"] = "$projectDir"


// Detect if we are in a CI build
if (project.hasProperty("forceCI")) {
    ext["ciBuild"] = true
} else {
    ext["ciBuild"] = project.hasProperty("teamcity")
}

val branch = GitBasedVersioning.getGitBranch()
if(ext["ciBuild"] == true){
    val buildNumber = System.getenv("BUILD_NUMBER")
    println("gitBranch: " + branch)

    if(releaseBuild && branch.equals("master")){
        rcpVersion = "$majorVersion.$minorVersion.$buildNumber.${GitBasedVersioning.getGitShortCommitHash()}"
    }
    else{
        rcpVersion = "$majorVersion.$minorVersion.$branch-SNAPSHOT"
    }
}

println("Java location: " + jdk_home)
println("Java version: " + JavaVersion.current())
println("gradle version: " + project.gradle.gradleVersion)


println("RCP version: " + rcpVersion)
println("MPS home directory: " + mpsHomeDir)
println("Artifacts directory: " + artifactsDir)
println("Build directory: " + buildDir + "\n")
println("CI build: "+ ext["ciBuild"])

fun winDistroVersion(weekFromNow: Int): String {
    val weekOfYear = LocalDate.now().get(WeekFields.ISO.weekOfYear())
    return "$majorVersion.$minorVersion.develop-KW${Math.floorMod(weekOfYear + weekFromNow, 53)}-SNAPSHOT"
}
fun winDistroVersion(): String = winDistroVersion(0);
fun winDistroVersionOfLastWeek(): String = winDistroVersion(-1);


println("winDistroVersion: " + winDistroVersion())
println("winDistroVersionOfLastWeek: " + winDistroVersionOfLastWeek())

// Set dependencies
dependencies {
    mps("com.jetbrains:mps:$mpsVersion")
    junitAnt("org.apache.ant:ant-junit:1.10.6")
    // iets3("org.iets3:opensource:2020.3.5094.6bd9f15@tgz")
    mbeddr("com.mbeddr:platform:2020.3.23001.6927e1d")
    jdk("com.jetbrains.jdk:jbrsdk:11_0_10-b1341.41:linux-x64@tgz")
    jdk("com.jetbrains.jdk:jbrsdk:11_0_10-b1341.41:windows-x64@tgz")
    jdk("com.jetbrains.jdk:jbrsdk:11_0_10-b1341.41:osx-x64@tgz")
    configurations["api"](files("$mpsHomeDir/lib/log4j.jar", "$mpsHomeDir/lib/jna.jar", "$mpsHomeDir/lib/jna-platform.jar"))
}

// Configure model check plugin
mkdir("${buildDir}/test-results")
configure<de.itemis.mps.gradle.modelcheck.ModelCheckPluginExtensions> {
    projectLocation = file("$mpsProjectDir")
    mpsConfig = mps
    javaExec = File(jdk_home, "bin/java")
    macros = listOf(Macro("libre.doge.gradlegithubactions.git.home", "$projectDir"))
    junitFile = file("${buildDir}/test-results/modelcheck-results.xml")
    errorNoFail = true
}

ext["itemis.mps.gradle.ant.defaultScriptArgs"] = listOf("-Dlibre.doge.gradlegithubactions.git.home=$projectDir", "-Dmps_home=$mpsHomeDir", "-Dartifacts.root=$artifactsDir", "-Dbuild.dir=$buildDir", "-Dmps.generator.skipUnmodifiedModels=true")
ext["itemis.mps.gradle.ant.defaultScriptClasspath"] = files(junitAnt.resolve())
ext["itemis.mps.gradle.ant.defaultJavaExecutable"] = File(jdk_home, "bin/java")


// Setup tasks
val TASK_GROUP_SETUP = "Setup"
val TASK_GROUP_BUILD = "Build"
val TASK_GROUP_VERIFICATION = "Verification"
val TASK_GROUP_PACKAGING = "Packaging"
val TASK_GROUP_UTIL = "Utility"
val TASK_GROUP_INSTALLER = "Installer"
val TASK_GROUP_UPDATER = "Updater"

val setup = tasks.register("setup"){
    group = TASK_GROUP_SETUP
    description = "Prepares for project build. Resolves build dependencies and generated libraries XML."
    dependsOn(resolveJdk)
    dependsOn(resolveMps)
    dependsOn(resolveIets3)
    dependsOn(tasks.getByName("downloadJbr"))
    dependsOn(generateLibrariesXml)
}

val resolveJdk = tasks.register<Copy>("resolveJdk"){
    group = TASK_GROUP_SETUP
    description = "Resolves jetbrains jdk 11 dependency."
    dependsOn(jdk)
    from(jdk.resolve())
    into(jdkDir)

    rename {
        filename ->
        val resolvedArtifact = jdk.resolvedConfiguration.resolvedArtifacts.find { ra -> ra.file.name == filename }
        resolvedArtifact?.name + "-" + resolvedArtifact?.classifier + "." + resolvedArtifact?.extension
    }
}

val resolveMps = tasks.register<Copy>("resolveMps") {
    description = "Resolves MPS dependency."
    group = TASK_GROUP_SETUP
    from(mps.resolve().map {zipTree(it)})
    into(mpsHomeDir)
}

val resolveIets3 = tasks.register<Copy>("resolveIets3") {
    description = "Resolves IETS3 dependency."
    group = TASK_GROUP_SETUP
    from(iets3.resolve().map { zipTree(it) })
    into(artifactsDir)
}

val generateLibrariesXml = tasks.register<Copy>("generateLibrariesXml"){
    description = "Generates libraries XML."
    group = TASK_GROUP_SETUP
    from("./libraries.xml.example")
    into("./code/libre.doge.gradlegithubactions/.mps/")
    rename("libraries.xml.example","libraries.xml")
}

val testLanguages = tasks.register<BuildLanguages>("testLanguages") {
    description = "Builds project languages."
    group = TASK_GROUP_BUILD
    dependsOn(setup)
    script = file("$buildDir/build.xml")
}

val buildLanguages = tasks.register<BuildLanguages>("buildLanguages") {
    description = "Builds project languages."
    group = TASK_GROUP_BUILD
    dependsOn(setup)
    script = file("$buildDir/build.xml")
}


project.afterEvaluate {
    // "checkmodels" task contributed by the "modelcheck" plugin should run after copying stub plugins
    tasks.getByPath("checkmodels")
}

val modelCheck = tasks.register("checkModel"){
    description = "Runs model checker. The Results are available in build/test-results."
    group = TASK_GROUP_VERIFICATION
    dependsOn(setup)
    dependsOn("checkmodels")
}

sourceSets{
    main{
        java.srcDir("updater/src")
    }
}

tasks.getByPath("compileJava").dependsOn(resolveMps)
val compileUpdater = tasks.register<Jar>("compileUpdater"){
    from(sourceSets["main"].output)
    setProperty("archiveFileName", "updater.jar")
}

