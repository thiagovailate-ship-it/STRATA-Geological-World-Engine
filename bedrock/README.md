# STRATA Bedrock add-on

## Download

[Download STRATA for Bedrock 1.26.52](releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon)

Open the `.mcaddon` file on a Bedrock device to import its behavior and resource packs. In a **new disposable test world**, enable both packs. In Creative, search for **STRATA Geological Compass** or use `/give @s strata:geology_compass` with cheats; use it from the Overworld to enter the optional scripted geology dimension and use it again there to return. The dimension does not replace Overworld terrain. See [`../docs/BEDROCK_GEOLOGY.md`](../docs/BEDROCK_GEOLOGY.md) for its algorithm and measured offline audit, and [`../docs/BEDROCK_PORT.md`](../docs/BEDROCK_PORT.md) for installation, API/experiment-toggle uncertainty, test steps and limitations.

## Rebuild

From the repository root:

```bash
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

The checked-in binary is a convenience download of the source packs in this directory. It has passed static JSON, reference, and archive validation; an in-game Bedrock 1.26.52 smoke/visual test is still outstanding.
