if [[ "${#}" -lt "2" || "${1}" == "--help" ]]; then
    echo "usage: ${0} DATA_INTEGRATION_HOME ZOOKEEPER_CONNECT_STRING"
    exit 1
fi

BASEDIR=$1
ZOOKEEPER=$2


for f in `find $BASEDIR/libext -type f -name "*.jar"` `find $BASEDIR/libext -type f -name "*.zip"`
do
  CLASSPATH=$CLASSPATH:$f
done

java -cp "${CLASSPATH}" com.mozilla.bugzilla_etl.lily.Types "${ZOOKEEPER}"
