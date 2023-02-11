Name:           parquet-mr-tools
Version:        1.12.0 
Release:        1%{?dist}
Summary:        Parquet File CLI (Powered By Parquet MR) 
BuildArch:	noarch

License:        Apache License 
URL:            https://github.com/DataDynamics/parquet-mr-tools 
Source0:        %{name}-%{version}.tar.gz

Requires:       bash

%description
Parquet File CLI (Powered By Parquet MR)

%install
rm -rf /opt/parquet-mr-tools
tar xvfz parquet-mr-tools-1.12.0.tar.gz -C /
cp /opt/parquet-mr-tools/bin/parquet '%{_bindir}'

%clean
rm -rf '%{_bindir}'/parquet
rm -rf /opt/parquet-mr-tools

%files
/opt/parquet-mr-tools/lib/parquet-common-1.12.3.jar
/opt/parquet-mr-tools/lib/parquet-column-1.12.3.jar
/opt/parquet-mr-tools/lib/hadoop-mapreduce-client-app-2.10.1.jar
/opt/parquet-mr-tools/lib/jackson-core-asl-1.9.13.jar
/opt/parquet-mr-tools/lib/junit-4.13.1.jar
/opt/parquet-mr-tools/lib/jackson-jaxrs-1.8.3.jar
/opt/parquet-mr-tools/lib/hadoop-mapreduce-client-common-2.10.1.jar
/opt/parquet-mr-tools/lib/hadoop-yarn-api-2.10.1.jar
/opt/parquet-mr-tools/lib/json-smart-2.3.jar
/opt/parquet-mr-tools/lib/guava-27.0.1-jre.jar
/opt/parquet-mr-tools/lib/asm-5.0.4.jar
/opt/parquet-mr-tools/lib/animal-sniffer-annotations-1.17.jar
/opt/parquet-mr-tools/lib/jackson-annotations-2.13.2.jar
/opt/parquet-mr-tools/lib/failureaccess-1.0.1.jar
/opt/parquet-mr-tools/lib/spotbugs-annotations-3.1.9.jar
/opt/parquet-mr-tools/lib/jersey-server-1.9.jar
/opt/parquet-mr-tools/lib/hadoop-auth-2.10.1.jar
/opt/parquet-mr-tools/lib/hadoop-common-2.10.1.jar
/opt/parquet-mr-tools/lib/error_prone_annotations-2.2.0.jar
/opt/parquet-mr-tools/lib/commons-math3-3.1.1.jar
/opt/parquet-mr-tools/lib/jets3t-0.9.0.jar
/opt/parquet-mr-tools/lib/easymock-3.4.jar
/opt/parquet-mr-tools/lib/slf4j-api-1.7.22.jar
/opt/parquet-mr-tools/lib/parquet-format-structures-1.12.3.jar
/opt/parquet-mr-tools/lib/mssql-jdbc-6.2.1.jre7.jar
/opt/parquet-mr-tools/lib/asm-3.1.jar
/opt/parquet-mr-tools/lib/activation-1.1.jar
/opt/parquet-mr-tools/lib/hadoop-mapreduce-client-jobclient-2.10.1.jar
/opt/parquet-mr-tools/lib/j2objc-annotations-1.1.jar
/opt/parquet-mr-tools/lib/zookeeper-3.4.14.jar
/opt/parquet-mr-tools/lib/jackson-databind-2.13.2.2.jar
/opt/parquet-mr-tools/lib/accessors-smart-1.2.jar
/opt/parquet-mr-tools/lib/jetty-sslengine-6.1.26.jar
/opt/parquet-mr-tools/lib/commons-lang-2.6.jar
/opt/parquet-mr-tools/lib/commons-pool-1.6.jar
/opt/parquet-mr-tools/lib/objenesis-2.2.jar
/opt/parquet-mr-tools/lib/zstd-jni-1.5.0-1.jar
/opt/parquet-mr-tools/lib/jsp-api-2.1.jar
/opt/parquet-mr-tools/lib/opencsv-2.3.jar
/opt/parquet-mr-tools/lib/commons-codec-1.4.jar
/opt/parquet-mr-tools/lib/hadoop-mapreduce-client-shuffle-2.10.1.jar
/opt/parquet-mr-tools/lib/api-asn1-api-1.0.0-M20.jar
/opt/parquet-mr-tools/lib/jsch-0.1.55.jar
/opt/parquet-mr-tools/lib/log4j-1.2.17.jar
/opt/parquet-mr-tools/lib/curator-framework-2.13.0.jar
/opt/parquet-mr-tools/lib/commons-logging-1.1.3.jar
/opt/parquet-mr-tools/lib/jettison-1.1.jar
/opt/parquet-mr-tools/lib/stax-api-1.0-2.jar
/opt/parquet-mr-tools/lib/ehcache-3.3.1.jar
/opt/parquet-mr-tools/lib/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
/opt/parquet-mr-tools/lib/htrace-core4-4.1.0-incubating.jar
/opt/parquet-mr-tools/lib/apacheds-i18n-2.0.0-M15.jar
/opt/parquet-mr-tools/lib/java-xmlbuilder-0.4.jar
/opt/parquet-mr-tools/lib/parquet-cli-1.12.3-runtime.jar
/opt/parquet-mr-tools/lib/audience-annotations-0.13.0.jar
/opt/parquet-mr-tools/lib/commons-net-3.1.jar
/opt/parquet-mr-tools/lib/avro-1.10.2.jar
/opt/parquet-mr-tools/lib/protobuf-java-2.5.0.jar
/opt/parquet-mr-tools/lib/servlet-api-2.5.jar
/opt/parquet-mr-tools/lib/commons-cli-1.2.jar
/opt/parquet-mr-tools/lib/xmlenc-0.52.jar
/opt/parquet-mr-tools/lib/commons-configuration-1.6.jar
/opt/parquet-mr-tools/lib/okhttp-2.7.5.jar
/opt/parquet-mr-tools/lib/hadoop-yarn-client-2.10.1.jar
/opt/parquet-mr-tools/lib/commons-beanutils-1.9.4.jar
/opt/parquet-mr-tools/lib/hadoop-hdfs-client-2.10.1.jar
/opt/parquet-mr-tools/lib/jetty-util-6.1.26.jar
/opt/parquet-mr-tools/lib/geronimo-jcache_1.0_spec-1.0-alpha-1.jar
/opt/parquet-mr-tools/lib/gson-2.2.4.jar
/opt/parquet-mr-tools/lib/netty-3.10.6.Final.jar
/opt/parquet-mr-tools/lib/hadoop-client-2.10.1.jar
/opt/parquet-mr-tools/lib/hadoop-yarn-common-2.10.1.jar
/opt/parquet-mr-tools/lib/commons-collections-3.2.2.jar
/opt/parquet-mr-tools/lib/parquet-avro-1.12.3.jar
/opt/parquet-mr-tools/lib/leveldbjni-all-1.8.jar
/opt/parquet-mr-tools/lib/slf4j-log4j12-1.7.22.jar
/opt/parquet-mr-tools/lib/jetty-6.1.26.jar
/opt/parquet-mr-tools/lib/parquet-encoding-1.12.3.jar
/opt/parquet-mr-tools/lib/jackson-mapper-asl-1.9.13.jar
/opt/parquet-mr-tools/lib/commons-text-1.8.jar
/opt/parquet-mr-tools/lib/stax2-api-3.1.4.jar
/opt/parquet-mr-tools/lib/commons-digester-1.8.jar
/opt/parquet-mr-tools/lib/hadoop-yarn-server-common-2.10.1.jar
/opt/parquet-mr-tools/lib/curator-recipes-2.13.0.jar
/opt/parquet-mr-tools/lib/jcip-annotations-1.0-1.jar
/opt/parquet-mr-tools/lib/parquet-jackson-1.12.3.jar
/opt/parquet-mr-tools/lib/jackson-core-2.13.2.jar
/opt/parquet-mr-tools/lib/hadoop-mapreduce-client-core-2.10.1.jar
/opt/parquet-mr-tools/lib/woodstox-core-5.0.3.jar
/opt/parquet-mr-tools/lib/jaxb-api-2.2.2.jar
/opt/parquet-mr-tools/lib/snappy-java-1.1.8.3.jar
/opt/parquet-mr-tools/lib/commons-lang3-3.9.jar
/opt/parquet-mr-tools/lib/jaxb-impl-2.2.3-1.jar
/opt/parquet-mr-tools/lib/curator-client-2.13.0.jar
/opt/parquet-mr-tools/lib/parquet-hadoop-1.12.3.jar
/opt/parquet-mr-tools/lib/commons-io-2.7.jar
/opt/parquet-mr-tools/lib/apacheds-kerberos-codec-2.0.0-M15.jar
/opt/parquet-mr-tools/lib/api-util-1.0.0-M20.jar
/opt/parquet-mr-tools/lib/jersey-client-1.9.jar
/opt/parquet-mr-tools/lib/commons-compress-1.20.jar
/opt/parquet-mr-tools/lib/HikariCP-java7-2.4.12.jar
/opt/parquet-mr-tools/lib/hadoop-annotations-2.10.1.jar
/opt/parquet-mr-tools/lib/checker-qual-2.5.2.jar
/opt/parquet-mr-tools/lib/nimbus-jose-jwt-7.9.jar
/opt/parquet-mr-tools/lib/jackson-xc-1.8.3.jar
/opt/parquet-mr-tools/lib/jersey-json-1.9.jar
/opt/parquet-mr-tools/lib/jersey-core-1.9.jar
/opt/parquet-mr-tools/lib/hadoop-yarn-registry-2.10.1.jar
/opt/parquet-mr-tools/lib/okio-1.6.0.jar
/opt/parquet-mr-tools/lib/httpcore-4.4.4.jar
/opt/parquet-mr-tools/lib/jcommander-1.72.jar
/opt/parquet-mr-tools/lib/httpclient-4.5.2.jar
/opt/parquet-mr-tools/lib/jsr305-3.0.2.jar
/opt/parquet-mr-tools/lib/hamcrest-core-1.3.jar
/opt/parquet-mr-tools/bin/parquet
'%{_bindir}'/parquet

%changelog
* 2023-02-11 KIM BYOUNG GON <architect@data-dynamics.io> - 1.12.0
- Extracted from Parquet MR 1.12.0
