.PHONY: keys env build up down logs reset psql mongo

COMPOSE := docker compose -f docker-compose.yml

keys: ## Generate fresh RSA keys for the auth-service in .secrets/keys/
	@if [ -f .secrets/keys/private.pem ] && [ -f .secrets/keys/public.pem ]; then \
		echo "RSA keys already exist"; \
	else \
		./scripts/gen-keys.sh; \
	fi

env: ## Bootstrap a .env from .env.example if missing
	@if [ -f .env ]; then \
		echo ".env already exists"; \
	else \
		cp .env.example .env; \
		echo "Created .env (review the values!)"; \
	fi

build: ## Build and test every backend service
	./mvnw -B -ntp clean verify

up: env keys ## Bring up everything (after build)
	$(COMPOSE) --env-file .env up -d

down: ## Stop and remove containers
	$(COMPOSE) down

logs: ## Tail aggregated logs
	$(COMPOSE) logs -f --tail=50

reset: ## Wipe volumes and restart from scratch
	$(COMPOSE) down -v
	$(MAKE) up

psql: ## Open psql against the local Postgres
	$(COMPOSE) --env-file .env exec postgres sh -lc 'psql -U "$$POSTGRES_USER" "$$POSTGRES_DB"'

mongo: ## Open mongosh against the local Mongo
	$(COMPOSE) --env-file .env exec mongodb sh -lc 'mongosh -u "$$MONGO_INITDB_ROOT_USERNAME" -p "$$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin'

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'
