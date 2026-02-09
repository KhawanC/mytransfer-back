# MyTransfer - Backend

## 📋 Sobre o Projeto

O **MyTransfer Backend** é uma API REST robusta desenvolvida em Spring Boot para gerenciar transferências de arquivos peer-to-peer (P2P) em tempo real. O sistema permite que usuários criem sessões de transferência, compartilhem via QR Code ou hash, e transfiram arquivos de forma segura e eficiente.

## 🎯 Propósito

Fornecer uma solução backend completa para transferência de arquivos com:
- **Autenticação segura** via JWT e OAuth2
- **Comunicação em tempo real** via WebSocket/STOMP
- **Upload resiliente** com suporte a chunks e retomada
- **Controle de acesso** baseado em sessões e aprovações
- **Armazenamento escalável** com MinIO

## ✨ Principais Funcionalidades

### 🔐 Autenticação e Autorização
- **Autenticação JWT**: Tokens de acesso e refresh com expiração configurável
- **OAuth2 Google**: Login social integrado
- **Spring Security**: Proteção de endpoints e validação de permissões
- **Fingerprint**: Validação de dispositivo para segurança adicional

### 📁 Gerenciamento de Sessões
- **Criação de Sessões**: Sessões únicas para transferência de arquivos
- **Hash de Conexão**: Sistema de convite via hash único ou QR Code
- **Aprovação de Entrada**: Criador aprova ou rejeita solicitações de entrada
- **Controle de Participantes**: Gerenciamento de quem pode enviar/receber arquivos
- **Prazo de Expiração**: Sessões com tempo de vida configurável

### 📤 Upload de Arquivos
- **Upload em Chunks**: Arquivos divididos em partes para maior eficiência
- **Resumable Upload**: Retomada automática de uploads interrompidos
- **Validação de Integridade**: Verificação de chunks e validação de hash
- **Progresso em Tempo Real**: Notificações de progresso via WebSocket
- **Armazenamento MinIO**: Arquivos armazenados em object storage escalável

### 📥 Download de Arquivos
- **Download Seguro**: Validação de permissões antes do download
- **Proxy de Arquivos**: Streaming eficiente de arquivos do MinIO
- **Controle de Acesso**: Apenas participantes da sessão podem baixar

### 🔔 Notificações em Tempo Real
- **WebSocket/STOMP**: Comunicação bidirecional em tempo real
- **Notificações Personalizadas**: Eventos de entrada, aprovação, upload, etc.
- **Mensagens Privadas**: Notificações direcionadas por usuário
- **Broadcast**: Notificações para todos os participantes da sessão

### 🔒 Segurança
- **Validação de Permissões**: Verificação em cada operação
- **Rate Limiting**: Proteção contra abuse com Redis
- **CORS Configurável**: Controle de origem de requisições
- **Sanitização de Dados**: Validação de inputs com Bean Validation
- **Auditoria**: Logs detalhados de operações críticas

## 🏗️ Arquitetura

### Tecnologias e Frameworks

#### Core
- **Java 21**: Linguagem base
- **Spring Boot 3.5.10**: Framework principal
- **Maven**: Gerenciamento de dependências

#### Banco de Dados e Cache
- **MongoDB**: Banco de dados NoSQL para documentos
  - Armazenamento de usuários, sessões e metadados de arquivos
  - Índices otimizados para consultas rápidas
- **Redis**: Cache e gerenciamento de estado
  - Cache de dados frequentemente acessados
  - Controle de sessões ativas
  - Rate limiting
  - Lock distribuído para operações críticas

#### Mensageria e Comunicação
- **RabbitMQ**: Message broker para processamento assíncrono
  - Filas para processamento de uploads
  - Dead letter queues para tratamento de erros
- **WebSocket/STOMP**: Comunicação em tempo real
  - Notificações instantâneas
  - Atualizações de progresso
  - Eventos de sessão

#### Armazenamento
- **MinIO**: Object storage S3-compatible
  - Armazenamento de arquivos
  - Buckets organizados por sessão
  - Presigned URLs para acesso temporário

#### Segurança
- **Spring Security**: Framework de segurança
- **JJWT 0.12.6**: Geração e validação de tokens JWT
- **OAuth2 Client**: Integração com Google OAuth2

#### Utilitários
- **ZXing 3.5.3**: Geração de QR Codes
- **Bean Validation**: Validação de dados
- **Lombok**: Redução de boilerplate
- **Mongock 5.4.4**: Migrações de banco de dados

### Estrutura de Pacotes

```
br.com.khawantech.files/
├── auth/                          # Módulo de autenticação
│   ├── controller/               # Endpoints de autenticação
│   ├── service/                  # Lógica de auth e OAuth2
│   ├── dto/                      # DTOs de requisição/resposta
│   └── exception/                # Tratamento de erros de auth
├── user/                         # Módulo de usuários
│   ├── entity/                   # Entidade User
│   ├── repository/               # Repositório MongoDB
│   └── service/                  # Serviços de usuário
├── transferencia/                # Módulo principal de transferências
│   ├── controller/              # Controllers REST e WebSocket
│   ├── service/                 # Lógica de negócio
│   ├── repository/              # Repositórios MongoDB
│   ├── dto/                     # DTOs de transferência
│   ├── entity/                  # Entidades (Sessao, Arquivo)
│   ├── exception/               # Exceções customizadas
│   └── model/                   # Modelos auxiliares
├── config/                      # Configurações da aplicação
│   ├── SecurityConfig           # Configuração do Spring Security
│   ├── WebSocketConfig          # Configuração do WebSocket
│   ├── RedisConfig              # Configuração do Redis
│   ├── MinioConfig              # Configuração do MinIO
│   └── CorsConfig               # Configuração de CORS
└── migration/                   # Migrações Mongock
```

### Padrões de Projeto

- **MVC (Model-View-Controller)**: Separação de responsabilidades
- **Repository Pattern**: Abstração de acesso a dados
- **Service Layer**: Lógica de negócio centralizada
- **DTO Pattern**: Transferência de dados entre camadas
- **Exception Handler**: Tratamento centralizado de exceções
- **Dependency Injection**: Inversão de controle com Spring

### Fluxo de Dados

```
Cliente HTTP/WebSocket
    ↓
Controller (REST/WebSocket)
    ↓
Service Layer
    ↓
Repository/External Services
    ↓
MongoDB/Redis/MinIO/RabbitMQ
```

## 🚀 Configuração e Deploy

### Pré-requisitos
- Java 21+
- Maven 3.8+
- MongoDB 6.0+
- Redis 7.0+
- RabbitMQ 3.12+
- MinIO (ou S3 compatível)

### Variáveis de Ambiente

```properties
# MongoDB
MONGODB_HOST=localhost
MONGODB_PORT=27017
MONGODB_DATABASE=mytransfer
MONGODB_USERNAME=
MONGODB_PASSWORD=
MONGODB_AUTH_DATABASE=admin

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=
REDIS_PASSWORD=

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=transferencias

# JWT
JWT_SECRET=your-secret-key-min-32-chars
JWT_ACCESS_TOKEN_EXPIRATION=3600000
JWT_REFRESH_TOKEN_EXPIRATION=2592000000

# OAuth2
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

# Application
APP_BASE_URL=http://localhost:8080
APP_FRONTEND_URL=http://localhost:3000
```

### Build e Execução

```bash
# Build
./mvnw clean package

# Executar
./mvnw spring-boot:run

# Docker
docker build -t mytransfer-backend .
docker run -p 8080:8080 --env-file .env mytransfer-backend
```

### Endpoints Principais

#### Autenticação
- `POST /api/auth/register` - Registro de usuário
- `POST /api/auth/login` - Login
- `POST /api/auth/refresh` - Refresh token
- `GET /api/auth/me` - Dados do usuário autenticado
- `GET /oauth2/authorization/google` - Iniciar OAuth2 Google

#### Sessões
- `GET /api/transferencia/sessoes` - Listar sessões do usuário
- `POST /api/transferencia/sessao` - Criar nova sessão
- `POST /api/transferencia/sessao/entrar` - Entrar em sessão
- `POST /api/transferencia/sessao/aprovar` - Aprovar entrada
- `POST /api/transferencia/sessao/rejeitar` - Rejeitar entrada
- `GET /api/transferencia/sessao/{id}` - Buscar sessão

#### Arquivos
- `POST /api/transferencia/arquivo/iniciar` - Iniciar upload
- `GET /api/transferencia/arquivo/{id}/chunks-pendentes` - Chunks pendentes
- `GET /api/transferencia/sessao/{id}/arquivos` - Listar arquivos
- `GET /api/proxy/arquivo/{id}` - Download de arquivo

#### WebSocket
- `/ws` - Endpoint de conexão WebSocket
- `/app/arquivo/chunk` - Enviar chunk de arquivo
- `/app/arquivo/finalizar` - Finalizar upload
- `/user/queue/notificacoes` - Receber notificações

## 🔒 Segurança

### Implementações de Segurança
- ✅ Validação de permissões em todas as operações
- ✅ Rate limiting com Redis
- ✅ Sanitização de inputs
- ✅ CORS configurável
- ✅ CSRF protection
- ✅ Auditoria de logs
- ✅ Tokens com expiração
- ✅ Validação de fingerprint
- ✅ Proteção contra upload malicioso

### Documentação Adicional
- Veja [ANALISE_SEGURANCA.md](../ANALISE_SEGURANCA.md) para análise detalhada
- Veja [SEGURANCA_IMPLEMENTACAO.md](../SEGURANCA_IMPLEMENTACAO.md) para implementações

## 📝 Licença

Este projeto é privado e proprietário.

## 👥 Autores

Desenvolvido por KhawanTech
