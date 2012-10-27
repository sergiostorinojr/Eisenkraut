import AssemblyKeys._

name := "Eisenkraut"

version := "1.0.0"

organization := "de.sciss"

description := "A multi-channel and hi-res capable audio file editor"

homepage := Some( url( "https://github.com/Sciss/Eisenkraut" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

crossPaths := false  // this is just a Java project right now!

retrieveManaged := true

mainClass := Some( "de.sciss.eisenkraut.Main" )

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/Eisenkraut.git</url>
  <connection>scm:git:git@github.com:Sciss/Eisenkraut.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- packaging ----

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundle.settings: _* )

appbundle.signature := "EisK"

appbundle.icon := Some( file( "src/main/resources/de/sciss/eisenkraut/application.png" ))

appbundle.documents ++= Seq(
   appbundle.Document( "AIFF Audio File", role = appbundle.Document.Editor, rank = appbundle.Document.Alternate,
                       icon = Some( file( "icons/aiff.icns" )),
                       extensions = Seq( "aif", "aiff", "aifc" ), osTypes = Seq( "AIFF", "AIFC" ),
                       mimeTypes = Seq( "audio/aiff", "audio/x-aiff" )),
   appbundle.Document( "WAVE Audio File", role = appbundle.Document.Editor, rank = appbundle.Document.Alternate,
                       icon = Some( file( "icons/wave.icns" )),
                       extensions = Seq( "wav", "wave" ), osTypes = Seq( "WAVE", "WAV ", "wave", "wav " ),
                       mimeTypes = Seq( "audio/wav", "audio/x-wav" )),
   appbundle.Document( "Wave64 Audio File", role = appbundle.Document.Editor, rank = appbundle.Document.Alternate,
                       icon = Some( file( "icons/wave64.icns" )),
                       extensions = Seq( "w64" ), osTypes = Seq( "W64 " )),
   appbundle.Document( "IRCAM Audio File", role = appbundle.Document.Editor, rank = appbundle.Document.Alternate,
                       icon = Some( file( "icons/ircam.icns" )),
                       extensions = Seq( "irc" )),
   appbundle.Document( "NeXT/Sun Audio File", role = appbundle.Document.Editor, rank = appbundle.Document.Alternate,
                       icon = Some( file( "icons/au.icns" )),
                       extensions = Seq( "snd", "au" ), osTypes = Seq( "AU  ", "au  ", "snd " ),
                       mimeTypes = Seq( "audio/au" )),
   appbundle.Document( "Waveform Cache File", role = appbundle.Document.Editor, rank = appbundle.Document.Owner,
                       icon = Some( file( "icons/cache.icns" )),
                       extensions = Seq( "cache" ))
)

appbundle.javaOptions ++= Seq( "-ea", "-Xmx2048m" )

appbundle.target <<= baseDirectory
