.PHONY: keys env build up down logs reset psql mongo

keys: ## Generate fresh RSA keys for the auth-service in .secrets/keys/
	@./scripts/gen-keys.sh

env: ## Bootstrap a .env from .env.example if missing
	@test -f .env || cp .env.example .env && echo "Created .env (review the values!)"

build: ## Build every service
	./mvnw -B -ntp -DskipTests clean install

up: env keys ## Bring up everything (after build)
	docker compose --env-file .env -f docker/docker-compose.yml up -d

down: ## Stop and remove containers
	docker compose -f docker/docker-compose.yml down

logs: ## Tail aggregated logs
	docker compose -f docker/docker-compose.yml logs -f --tail=50

reset: ## Wipe volumes and restart from scratch
	docker compose -f docker/docker-compose.yml down -v
	$(MAKE) up

psql: ## Open psql against the local Postgres
	docker exec -it postgres psql -U $${POSTGRES_USER:-ecommerce}

mongo: ## Open mongosh against the local Mongo
	docker exec -it mongodb mongosh -u $${MONGO_INITDB_ROOT_USERNAME:-ecommerce} -p $${MONGO_INITDB_ROOT_PASSWORD:-change_me_mongo}

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'
