
build-binary-local: cleanup uberjar graal-build

VERSION_FILE = resources/VERSION

BINARY_FILE = teleward-$(shell uname -s)-$(shell uname -m)

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
	-jar ./target/uberjar/teleward.jar \
	-H:IncludeResources='^VERSION$$' \
	--enable-url-protocols=http,https \
	-H:ReflectionConfigurationFiles=reflection-config.json \
	-H:+ReportExceptionStackTraces \
	-H:Log=registerResource \
	-H:Name=./builds/${BINARY_FILE}

build-binary-docker: uberjar
	docker-compose run compile

toc-install:
	npm install --save markdown-toc

toc-build:
	node_modules/.bin/markdown-toc -i README.md

release-build: build-binary-local build-binary-docker

TAG = teleward:latest

docker-build:
	docker build --no-cache -t ${TAG} -f Dockerfile .

docker-run:
	docker run -it --rm ${TAG}
