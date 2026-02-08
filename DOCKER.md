# ===========================================
# MyTransfer Backend - Docker Compose
# ===========================================

## 🚀 Comandos Rápidos

### Iniciar todos os serviços:
```bash
docker-compose up -d
```

### Parar todos os serviços:
```bash
docker-compose down
```

### Ver logs dos serviços:
```bash
# Todos os serviços
docker-compose logs -f

# Serviço específico
docker-compose logs -f mongodb
docker-compose logs -f redis
docker-compose logs -f rabbitmq
```

### Verificar status dos serviços:
```bash
docker-compose ps
```

### Remover volumes (cuidado: apaga dados persistentes):
```bash
docker-compose down -v
```

## 📦 Serviços Incluídos

### MongoDB
- **Porta:** 27017
- **Database:** mytransfer
- **URL de conexão:** `mongodb://localhost:27017/mytransfer`

### Redis
- **Porta:** 6379
- **Cache TTL:** 30 minutos (configurado na aplicação)

### RabbitMQ
- **Porta AMQP:** 5672
- **Management UI:** http://localhost:15672
- **Credenciais padrão:**
  - Usuário: `guest`
  - Senha: `guest`

## 🔧 Configuração da Aplicação

Certifique-se de que seu arquivo `.env` está configurado corretamente:

```env
MONGODB_URI=mongodb://localhost:27017/mytransfer
MONGODB_DATABASE=mytransfer
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

## ⚙️ Iniciar a Aplicação

1. Inicie os containers Docker:
   ```bash
   docker-compose up -d
   ```

2. Aguarde os serviços ficarem saudáveis (healthcheck):
   ```bash
   docker-compose ps
   ```

3. Execute a aplicação Spring Boot:
   ```bash
   ./mvnw spring-boot:run
   ```

## 🛠️ Troubleshooting

### Porta já em uso
Se alguma porta estiver em uso, você pode alterar no `docker-compose.yml`:
```yaml
ports:
  - "27018:27017"  # Trocar 27017 por outra porta
```

### Limpar cache e reiniciar
```bash
docker-compose down -v
docker-compose up -d
```

### Acessar MongoDB diretamente
```bash
docker exec -it mytransfer-mongodb mongosh
```

### Acessar Redis CLI
```bash
docker exec -it mytransfer-redis redis-cli
```
