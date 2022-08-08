
NI_TAG = ghcr.io/graalvm/native-image:22.2.0

TAG = teleward:latest

VERSION_FILE = resources/VERSION

PWD = $(shell pwd)

PLATFORM = PLATFORM

NI_ARGS = \
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
	-H:Name=./builds/teleward-

ni-args:
	echo ${NI_ARGS}

build-binary-local: cleanup uberjar graal-build

cleanup:
	rm -rf target

version:
	lein project-version > ${VERSION_FILE}

uberjar: version
	lein uberjar

graal-build: platform-local
	native-image ${NI_ARGS}$(shell cat ${PLATFORM})

platform-local:
	echo `uname -s`-`uname -m` > ${PLATFORM}

platform-docker:
	docker run -it --rm --entrypoint /bin/sh ${NI_TAG} -c 'echo `uname -s`-`uname -m`' > ${PLATFORM}

build-binary-docker: uberjar platform-docker
	docker run -it --rm -v ${PWD}:/build -w /build ${NI_TAG} ${NI_ARGS}$(shell cat ${PLATFORM})

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
