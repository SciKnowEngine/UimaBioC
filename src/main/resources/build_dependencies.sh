# Shellscript to load all dependencies for this project. 

git clone https://github.com/Simmetrics/simmetrics
cd simmetrics
git checkout 03144c50fc829a8681698b37e2a96be779fa8b66
2020-02-03T23:10:17.czirwc1osx627 mvn -DskipTests -Dmaven.javadoc.skip=true clean install
cd ..

git clone https://bitbucket.org/nicta_biomed/brateval.git
cd brateval
mvn clean install
cd ..

git clone https://github.com/openbiocuration/BioC_Java
cd BioC_Java
mvn clean install
cd ..
