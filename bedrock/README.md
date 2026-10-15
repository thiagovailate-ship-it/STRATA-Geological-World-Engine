# STRATA Bedrock add-on

## Download

[Download STRATA for Bedrock 1.26.52](releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon)

Open the `.mcaddon` file on a Bedrock device to import its behavior and resource packs. Create a **new** test world to check custom biome generation; see [`../docs/BEDROCK_PORT.md`](../docs/BEDROCK_PORT.md) for the port scope, experiment-toggle troubleshooting, validation status, and testing instructions.

## Rebuild

From the repository root:

```bash
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

The checked-in binary is a convenience download of the source packs in this directory. It has passed static JSON, reference, and archive validation; an in-game Bedrock 1.26.52 smoke/visual test is still outstanding.
