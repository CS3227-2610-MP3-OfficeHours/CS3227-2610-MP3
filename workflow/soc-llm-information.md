
Why Use SoCLaaS
What You Can Do With It
What SoCLaaS Is
Getting Started
SoCLaaS
SoCLaaS (SoC's LLM-as-a-Service) is a managed LLM API gateway for SoC users, giving you straightforward access to large language models through a familiar OpenAI-compatible API. At its core, SoCLaaS is the equivalent of OpenAI's API cloud service — hosted on our own infrastructure and running open-weight models. It is free: no payments or budget justifications required.

Whether you're building an app, developing a bot, automating a workflow, or just experimenting with AI, SoCLaaS has you covered. It works with any OpenAI-compatible client, including agent frameworks like Codex, and a web chat is also available. SoCLaaS offers a curated selection of open-weight models, ideal for learning, exploration, and prototyping. Audio transcription (Whisper) and embeddings APIs are provided as well.

If you are specifically looking for instructions on running AI coding agents in SoC servers, please refer to the AI Coding page.

If you use SoCLaaS, please join the SoCLaaS Announcements Telegram channel to stay up to date on news and developments.

Why Use SoCLaaS

SoCLaaS is designed to remove the friction so you can focus on what matters: building with AI.

With SoCLaaS, you can:

Use AI models through a familiar API — drop-in compatible with OpenAI clients
Keep spending personal — no need to pay out-of-pocket or chase team reimbursements
Skip procurement delays — no account creation, no purchase orders, no wait
Stick to one endpoint — build against a school-provided API instead of juggling multiple vendors
Offload the infrastructure — SoC manages the models so you don't have to
For most users, the payoff is simple: you get working API access to capable AI models without any administrative overhead.

What You Can Do With It

SoCLaaS works wherever you'd normally plug in an LLM API. Popular use cases include:

Chat and assistant features in applications
Summarization, rewriting, and content generation
Extracting structured data from unstructured text
Classification and tagging workflows
Code assistance and automation scripts
AI-powered prototyping and research experiments
It's also compatible with many existing tools, SDKs, and libraries that accept an OpenAI-compatible endpoint. For learning, exploration, and prototyping, SoCLaaS should be your first choice. If you later need production-scale reliability or specialized models, commercial providers are a natural next step.

What SoCLaaS Is

SoCLaaS is an API service for developers, researchers, and students. It's designed to be plugged into your apps, scripts, tools, and integrations.

Prefer a web interface? We also provide a simple Open WebUI frontend so you can start chatting with models immediately — no code required.


Get Access
To use SoCLaaS, you will need:

SoCLaaS API base URL (https://soclaas-api.comp.nus.edu.sg)
An API key
One or more approved model names
Get API Key

The easiest way to get your API key is to login to https://soclaas-portal.comp.nus.edu.sg and issue/rotate your API key.

The instructions below are for CLI/programmatic access.

Login to SoC login servers like stf.comp.nus.edu.sg (for staff) or stu.comp.nus.edu.sg (for students) and run:

soclaas-portal issueCopy
You will need to enter your SOC account password. This will generate an API key. Preferably, let the script update ~/.bashrc for you so that environment variables are setup for you automatically.

If you already have an API key issued before, you can get a new replacement key by running:

soclaas-portal issue –-rotateCopy
The old key will be replaced and will not work anymore.)

Treat the API key like a password:

Store it in environment variables or a secret manager
Do not commit it to git
Do not embed it in frontend code or mobile apps
Rotate it if you think it has been exposed
You can run soclaas-portal -h to get information on other commands, such as to revoke your key, get usage summary, usage records, and buget controls that apply to your key.

API Usage

Please refer to the API Reference for details. The Quick Start Guide provides a tutorial on using the API.

Web/Chat Access

SoCLaaS is meant to be an LLM API gateway service. The primary usage method is through API access (see quickstart). However, a user-friendly web chat interface using Open WebUI is also available at https://aichat.comp.nus.edu.sg. Note that getting an API Key is mandatory to use the Open WebUI.

Using with Codex

Make sure you have Codex installed first (refer to https://openai.com/codex/). Then, configure Codex by running:

soclaas-setup-codexCopy
This will create a separate profile from the regular Codex/OpenAI one. To use Codex with SoCLaaS, run:

codex --profile soclaasCopy
Installing outside of stf.comp.nus.edu.sg / stu.comp.nus.edu.sg

a) Set Up a Virtual Environment (Optional)

It is recommended to use a virtual environment to avoid dependency conflicts.

# 1. Create a virtual environment
python3 -m venv .venv

# 2. Activate the virtual environment
source .venv/bin/activateCopy
b) Install required Python package

pip install tomlkitCopy
c) Configure Environment Variables

export SOCLAAS_API_KEY="<your_api_key_here>"
export SOCLAAS_MODEL="<your_model_identifier>"
export SOCLAAS_BASE_URL=https://soclaas-api.comp.nus.edu.sg/v1Copy
You may also input the above codes into your ~/.bashrc. Remember to do a source ~/.bashrc to refresh the changes.

d) Install Codex CLI

curl -fsSL https://chatgpt.com/codex/install.sh | shCopy
e) Transfer SoCLaaS Set up file (from stf or stu server)

To view the SoCLaaS file:

cat /usr/local/bin/soclaas-setup-codexCopy
Copy the SoCLaaS set up file to your local directory on your server and execute.

scp /usr/local/bin/soclaas-setup-codex id@yourserver:
chmod +x ~/soclaas-setup-codex
./soclaas-setup-codexCopy
f) Verification

After completing the above steps, verify that SoCLaaS is correctly installed by running:

codex --profile soclaas

