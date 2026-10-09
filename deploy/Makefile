# ============================================================================
# z-surl Makefile —— 统一入口（005 规范 §1.3）
#
#   make help        看所有目标
#   make build       编译 admin jar（不含测试）
#   make test        跑单测
#   make verify      单测 + house-style 闸门 + 依赖树检查（收口验证）
#   make fe          构建前端两层（组件层 → 应用层）
#   make images      构建前后端镜像
#   make dev         Mode 1 合体（单容器）
#   make split       Mode 2 分体（前端独立 nginx）
#   make cluster     Mode 3 集群（1 前端 + 3 后端）
#   make down        停掉当前 compose
#   make k8s-apply   部署到 k8s
#   make run-local   本机 java -jar（H2 内存库，零外部依赖）
# ============================================================================

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

DEPLOY_DIR := deploy
BIN        := $(DEPLOY_DIR)/bin
MAVEN_ARGS ?= -B

.DEFAULT_GOAL := help
.PHONY: help build test verify fe images dev split cluster down k8s-apply run-local clean

## ---------- 文档 ----------

help: ## 显示本帮助
	@echo "z-surl —— 可用目标："
	@echo ""
	@# GNU Make 的 `##` 帮助注释写在**目标后面**，且允许带前置依赖：
	@#   build: ## 说明              （无依赖）
	@#   verify: test ## 说明        （有依赖，冒号和 ## 之间夹着依赖名）
	@# 所以锚点只能到冒号为止，## 用 .* 匹配 —— 写成 ':[[:space:]]+##' 会把所有
	@# "带依赖的目标"（verify/dev/split/cluster）整批漏掉，而 help 恰恰最需要它们。
	@grep -E '^[a-zA-Z][a-zA-Z0-9_-]*:.*[[:space:]]##[[:space:]]' $(MAKEFILE_LIST) \
		| sed -E 's/^([a-zA-Z][a-zA-Z0-9_-]*):.*##[[:space:]]*/\1|/' \
		| awk -F'|' '{printf "  %-12s %s\n", $$1, $$2}'
	@echo ""
	@echo "示例："
	@echo "  make fe && make images && make dev     # 完整跑通 Mode 1"
	@echo "  make run-local                          # 不装 docker，本机 H2 起服务"
	@echo "  make verify                            # 收口验证（CI 门禁用这条）"

## ---------- 构建 ----------

build: ## 编译全部模块（跳过测试）
	mvn $(MAVEN_ARGS) clean install -DskipTests

test: ## 跑单测
	mvn $(MAVEN_ARGS) clean test

verify: test ## 收口验证：单测 + house-style 闸门 + 依赖树无 com.zifang
	@echo "==> house-style 闸门"
	@bash $(DEPLOY_DIR)/../_doc/003_script/house-style-gate.sh
	@echo "==> 依赖树不得出现 com.zifang（死 SNAPSHOT 防线）"
	@mvn $(MAVEN_ARGS) -o dependency:tree -pl z-surl-starter -am 2>&1 \
		| grep -E "com\.zifang:" && { echo "FAIL: 依赖树里有 com.zifang"; exit 1; } \
		|| echo "OK: 0 com.zifang hits"
	@echo "==> admin 不得进 Central（deploy.skip 必须为 true）"
	@grep -q "<maven.deploy.skip>true</maven.deploy.skip>" z-surl-admin/pom.xml \
		&& echo "OK: z-surl-admin deploy.skip=true" \
		|| { echo "FAIL: z-surl-admin 没有 deploy.skip=true"; exit 1; }

## ---------- 前端 ----------

fe: ## 构建前端两层（组件层先出 d.ts + 产物自检，再出 SPA）
	cd _frontend/z-surl-component && npm install --no-audit --no-fund && npm run build
	cd _frontend/z-surl-suit && npm install --no-audit --no-fund && npm run build

fe-dev: ## 前端热更新（配合 mvn spring-boot:run 用）
	cd _frontend/z-surl-suit && npm run dev

## ---------- 镜像与部署 ----------

images: ## 构建前后端镜像
	bash $(BIN)/build-images.sh

dev: images ## Mode 1 合体（单容器）
	bash $(BIN)/start-compose.sh up dev

split: images ## Mode 2 分体（前端独立 nginx）
	bash $(BIN)/start-compose.sh up split

cluster: images ## Mode 3 集群（1 前端 + 3 后端）
	bash $(BIN)/start-compose.sh up cluster

down: ## 停止并移除当前 compose 的容器
	@MODE="$${MODE:-dev}"; \
	case "$$MODE" in \
	  split)   bash $(BIN)/start-compose.sh down split ;; \
	  cluster) bash $(BIN)/start-compose.sh down cluster ;; \
	  *)       bash $(BIN)/start-compose.sh down dev ;; \
	esac

k8s-apply: ## 部署到 k8s（Secret 现场生成，不从文件 apply）
	bash $(BIN)/deploy-k8s.sh

## ---------- 本机直跑 ----------

run-local: ## 本机 java -jar（H2 内存库，不连 MySQL）
	@echo "==> 启动 z-surl-admin（dev profile = H2）"
	@echo "    入口     http://localhost:8080"
	@echo "    健康检查 http://localhost:8080/actuator/health"
	@echo "    停       Ctrl-C"
	java -jar z-surl-admin/target/z-surl-admin-*-exec.jar \
		--spring.profiles.active=dev \
		--server.port=$${PORT:-8080}

## ---------- 清理 ----------

clean: ## 清构建产物
	mvn $(MAVEN_ARGS) clean
	@echo "==> 清理前端产物"
	@rm -rf _frontend/z-surl-component/dist \
	       _frontend/z-surl-component/node_modules \
	       _frontend/z-surl-suit/dist \
	       _frontend/z-surl-suit/node_modules \
	       _frontend/.node
