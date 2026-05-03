import json

with open(r'C:\Users\andyb\.gemini\antigravity\brain\88013508-b414-489c-8fc6-2be4403cd304\.system_generated\logs\overview.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for line in lines:
    if 'write_to_file' in line and 'implementation_plan.md' in line:
        try:
            data = json.loads(line)
            for call in data.get('tool_calls', []):
                if call['name'] == 'write_to_file' and 'implementation_plan.md' in call['args']['TargetFile']:
                    print('--- PLAN ---')
                    print(call['args']['CodeContent'])
        except Exception as e:
            pass
