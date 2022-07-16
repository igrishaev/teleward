
all: cleanup uberjar graal-build

VERSION_FILE = resources/VERSION

cleanup:
	rm -rf target

version:
	lein project-version > ${VERSION_FILE}

uberjar: version
	lein uberjar

graal-build:
	native-image \
	--report-unsupported-elements-at-runtime \
	--initialize-at-build-time \
	--no-fallback \
	--allow-incomplete-classpath \
	-jar ./target/uberjar/BAXTEP.jar \
	-H:IncludeResources='^logback.xml$$' \
	-H:IncludeResources='^VERSION$$' \
	--enable-url-protocols=http,https \
	-H:ReflectionConfigurationFiles=reflection-config.json \
	--initialize-at-run-time=com.fasterxml.jackson.databind.DeserializationContext \
	--initialize-at-run-time=com.fasterxml.jackson.databind.ObjectReader \
	--initialize-at-run-time=com.fasterxml.jackson.databind.node.InternalNodeMapper \
	-H:+ReportExceptionStackTraces \
	-H:Log=registerResource \
	-H:Name=./target/BAXTEP

docker-build: uberjar
	docker-compose run compile

toc-install:
	npm install --save markdown-toc

toc-build:
	node_modules/.bin/markdown-toc -i README.md
