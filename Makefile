
NI_TAG = ghcr.io/graalvm/native-image:22.2.0

TAG = teleward:latest

VERSION_FILE = resources/VERSION

PWD = $(shell pwd)

PLATFORM = PLATFORM

JAR = target/uberjar/teleward.jar

NI_ARGS = \
	--initialize-at-build-time \
	--report-unsupported-elements-at-runtime \
	--no-fallback \
	-jar ${JAR} \
	-H:IncludeResources='^VERSION$$' \
	-H:IncludeResources='^config.edn$$' \
	--enable-http \
	--enable-https \
	-H:+PrintClassInitialization \
	-H:ReflectionConfigurationFiles=reflection-config.json \
	-H:+ReportExceptionStackTraces \
	-H:Log=registerResource \
	-H:Name=./builds/teleward-

ni-args:
	echo ${NI_ARGS}

build-binary-local: ${JAR} graal-build

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

build-binary-docker: ${JAR} platform-docker
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

ydb-jar:
	lein with-profile +ydb uberjar

ydb-repl:
	lein with-profile +ydb repl

PACKAGE=package.zip

bash-package: ydb-jar build-binary-docker
	zip -j ${PACKAGE} conf/handler.sh builds/teleward-Linux-x86_64
