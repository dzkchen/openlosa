.PHONY: dev up down logs stop clean

# Start the full local stack (Docker services + backend + frontend, live reload).
dev:
	./scripts/dev.sh

# Bring up just the Docker services (mysql + engine) in the background.
up:
	docker compose --profile engine up -d

# Stop the Docker services (keeps the mysql_data volume).
down:
	docker compose --profile engine --profile email down

# Tail logs from the Docker services.
logs:
	docker compose logs -f

# Alias for down.
stop: down

# Stop everything AND drop the database volume (fresh migrations next start).
clean:
	docker compose --profile engine --profile email down -v
