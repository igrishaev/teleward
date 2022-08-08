
NE_TAG = ghcr.io/graalvm/native-image:22.2.0

TAG = teleward:latest

VERSION_FILE = resources/VERSION

BINARY_FILE = teleward-$(shell uname -s)-$(shell uname -m)

PWD = $(shell pwd)

NE_ARGS = \
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

ne-args:
	echo ${NE_ARGS}

build-binary-local: cleanup uberjar graal-build

cleanup:
	rm -rf target

version:
	lein project-version > ${VERSION_FILE}

uberjar: version
	lein uberjar

graal-build:
	native-image ${NE_ARGS}

build-binary-docker: uberjar
	docker run -it --rm -v ${PWD}:/build -w /build ${NE_TAG} ${NE_ARGS}

toc-install:
	npm install --save markdown-toc

toc-build:
	node_modules/.bin/markdown-toc -i README.md

release-build: build-binary-local build-binary-docker

docker-build:
	docker build --no-cache -t ${TAG} -f Dockerfile .

docker-run:
	docker run -it --rm ${TAG}

lint:
	clj-kondo --lint src
