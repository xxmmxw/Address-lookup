# Address Lookup Lambda (Java 17)

A small AWS Lambda that accepts `GET ?address=...`, calls NSW Spatial Services REST APIs to:
- geocode the address (lat/lon),
- fetch suburb name,
- fetch state electoral district,
  and returns a JSON payload.

**Runtime:** Java 17  
**Handler:** `au.gov.nsw.dcs.AddressLookupHandler::handleRequest`  
**Output JSON:** `address`, `location.lat/lon`, `suburb`, `state_electoral_district`, `identifiers`

---

## 1) Prerequisites

- **JDK 17** and **Maven 3.8+**
- **AWS CLI** (configured for your account/region)
- **Docker Desktop** (for local SAM emulation)
- **AWS SAM CLI**

> Region used in examples: **ap-southeast-2** (Sydney)

---

## 2) Install tools

### macOS (Intel/Apple Silicon) — via Homebrew

```bash
# Homebrew (if missing) — macOS:
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Update brew
brew update

# AWS SAM CLI
brew install aws-sam-cli
sam --version

# Docker Desktop

brew install --cask docker
open -a Docker          # start Docker Desktop; wait until it says "Docker Desktop is running"

# (Optional) jq for pretty JSON in terminal
brew install jq

# Verify Docker

docker run --rm hello-world
Windows — two good options
Option A: Native Windows (recommended)
Use winget (built into Windows 10/11):

powershell

# SAM CLI
winget install --id AWS.SAM-CLI -e

# Docker Desktop
winget install --id Docker.DockerDesktop -e
# Start Docker Desktop and ensure it says "Running"







Build & run locally:

sam validate -t template.yaml
sam build -t template.yaml                 # creates .aws-sam/build/template.yaml
sam local start-api -t .aws-sam/build/template.yaml

In another terminal:

curl -s "http://127.0.0.1:3000/address?address=346%20PANORAMA%20AVENUE%20BATHURST" | python3 -m json.tool
# Or:
# curl -s "http://127.0.0.1:3000/address?address=346%20PANORAMA%20AVENUE%20BATHURST" | jq


Common local errors & fixes

ClassNotFoundException: run sam build first; start with the built template.

Cannot connect to the Docker daemon: start Docker Desktop and wait until it’s running.

![Screenshot 2025-09-28 at 8.52.26 pm.png](../../../../../var/folders/04/_lv60bys3x50qr0330jrsw740000gn/T/TemporaryItems/NSIRD_screencaptureui_tqcLHx/Screenshot%202025-09-28%20at%208.52.26%E2%80%AFpm.png)