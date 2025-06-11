
Could you please generate PRD document for AWS server -basic migraiton from AWS to Azure
Use the following assumptions and constraints:


* Migration will be AI assisted using agentic AI tools, phased approach.
* Use this code base and documentation to analyze current solution
* top-down prompting approach will be used: PRD document first than prompts covering migration phases and then more fine grained prompts. 
* Migrated Azure server should use only dev environment in Azure, CI/CD using Github Actions, no need for higher level environment as for now
* Terrafrom should be used for IaaS for migrated azure hosted server
* Primary AI tooling: **Cursor or Github Copilot Agents** orchestrating **Claude Sonnet 4** & **Claude Opus 4**; **Gemini 2.5 PRO** used ad‑hoc.
* Migrate azure code should be located in a new sub folder(from root): authserver.azure
* Migrated azure server should also use hexgonal architecture with infrastructue layer to support Azure instead of AWS

