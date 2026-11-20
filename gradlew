#!/bin/sh

APP_HOME=`dirname "$0"`
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

warn () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }

cygwin=false
msys=false
darwin=false
case "`uname`" in
  CYGWIN* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MINGW* | MSYS* ) msys=true ;;
esac

PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then PRG="$link"; else PRG=`dirname "$PRG"`"/$link"; fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then JAVACMD="$JAVA_HOME/jre/sh/java"; else JAVACMD="$JAVA_HOME/bin/java"; fi
    if [ ! -x "$JAVACMD" ] ; then die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"; fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ "$cygwin" = "false" -a "$darwin" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -n 2>/dev/null` || true
    if [ "$MAX_FD_LIMIT" != "unlimited" ] ; then
        ulimit -n $MAX_FD_LIMIT 2>/dev/null || true
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -Dorg.gradle.appname=$APP_BASE_NAME -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
