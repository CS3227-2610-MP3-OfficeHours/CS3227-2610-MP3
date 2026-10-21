Quick Start Guide
For the following examples, set these environment variables:

export SOCLAAS_API_KEY="<your_api_key>"
export SOCLAAS_BASE_URL="https://soclaas-api.comp.nus.edu.sg/v1"Copy
List models:

curl -H "Authorization: Bearer $SOCLAAS_API_KEY" \
    "$SOCLAAS_BASE_URL/models"Copy
Send a chat completion request:

curl "$SOCLAAS_BASE_URL/chat/completions" \
    -H "Authorization: Bearer $SOCLAAS_API_KEY" \
    -H "Content-Type: application/json" \
    -d \
'{
  "model": "gemma4:26b",
  "messages": [
    {"role": "user", "content": "Say hello in one sentence."}
  ],
  "reasoning_effort": "none"
}'Copy
Example with the OpenAI Python client:

from openai import OpenAI
import os

client = OpenAI(
    api_key=os.environ["SOCLAAS_API_KEY"],
    base_url=f'{os.environ["SOCLAAS_BASE_URL"].rstrip("/")}',
)

response = client.chat.completions.create(
    model="qwen3.5:9b",
    messages=[{"role": "user", "content": "Say hello in one sentence."}],
    reasoning_effort="none"
)

print(response.choices[0].message.content)Copy
Common Errors

API endpoints may return these errors:

401 Unauthorized: Your API key is missing, invalid, or revoked.
403 Forbidden: Your key is not allowed to use the requested model or feature.
429 Too Many Requests: You hit a rate limit or quota limit.
400 Bad Request: The request body is invalid or missing required fields such as model.
5xx: SoCLaaS or an upstream model backend had a temporary issue. Retry if your request is safe to retry.
Good Practices

Use model names returned by GET /v1/models
Handle 429 and 5xx responses with retry/backoff where appropriate
Set request timeouts in your client
Start with small test prompts before integrating into production workflows
