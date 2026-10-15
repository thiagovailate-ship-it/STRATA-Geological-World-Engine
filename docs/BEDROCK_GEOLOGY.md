# STRATA Bedrock — modelo geológico e auditoria reproduzível

**Escopo:** algoritmo procedural para a dimensão opcional `strata:geology`, gerada pela Script API no Bedrock 1.26.52. Isto não altera o heightmap do Overworld. A dimensão é um ambiente separado de avaliação, não uma substituição aprovada para o Overworld.

## Referências e limites

| Referência | Como orienta o modelo | O que não se conclui |
|---|---|---|
| USGS, [Understanding plate motions](https://pubs.usgs.gov/gip/dynamic/understanding.html) | Convergência, divergência e movimento transformante são tratados de forma distinta; a interação entre crosta oceânica e continental orienta fossas, arcos e riftes. | O add-on não usa as geometrias nem as velocidades observadas das placas terrestres. |
| NOAA, [The Ocean](https://globalocean.noaa.gov/the-ocean/) | Referência de cerca de 71% de cobertura oceânica para calibrar uma janela regional de uma seed. | Uma janela plana de uma seed não é uma medição global ou esférica. |
| O’Callaghan & Mark (1984), [redes de drenagem D8](https://doi.org/10.1016/S0734-189X(84)80011-0) | Direção de fluxo em oito vizinhos e área contribuinte em grades. | O cálculo do add-on é local e não calibrado por vazões observadas. |
| Barnes, Lehman & Mulla (2014), [Priority-Flood](https://doi.org/10.1016/j.cageo.2013.04.024) | Preenchimento de depressões por fila de prioridade e saídas explícitas. | É uma adaptação limitada a patches, com bordas artificiais. |
| Whipple & Tucker (1999), [stream-power river incision](https://doi.org/10.1029/1999JB900120) | Motiva relacionar erosão fluvial com declividade e área contribuinte. | Coeficientes e unidades são heurísticos, não taxas físicas da Terra. |

## O que mudou para reduzir a aparência aleatória

O modelo anterior usava tipos de placa independentes por célula e um campo de elevação dominado por ruído em escalas relativamente curtas. A versão atual cria **continuidade tectônica em escala de continente** e faz a classificação das margens depender dos componentes do movimento relativo.

1. **Placas determinísticas e coerentes.** O plano é dividido em células Voronoi de 1.536 blocos, com centros deslocados por seed e margens deformadas por warp. O tipo de crosta não é mais sorteado independentemente em cada célula: deriva de um campo contínuo de 8.000 blocos, com limiar fixo `0.10`. A auditoria mede a fração oceânica resultante; o campo continua sintético.
2. **Movimento rígido local.** Cada placa tem uma translação e uma rotação 2D determinísticas. A velocidade em um ponto é calculada como translação mais rotação ao redor do centro da placa. A velocidade relativa é separada em componente normal à margem e componente tangencial: aproximação classifica convergência, afastamento classifica divergência e cisalhamento com pouca componente normal classifica transformação.
3. **Relevo condicionado pela interação.** Convergência entre crostas continentais produz uma faixa orogênica larga; convergência mista produz depressão de subducção e arco no lado continental; convergência oceano-oceano produz um arco insular mais baixo. Divergência gera crista sobretudo em crosta oceânica e rifte em crosta continental. Cisalhamento fica restrito a escarpas moderadas. A separação entre crosta oceânica e continental é ponderada para evitar degraus na troca de placa mais próxima.
4. **Campo continental e escala do relevo.** O continente deriva do bias das placas mais um campo de escala 6.000 blocos e offset `CONTINENTAL_OFFSET = -0.40`. A orogenia orienta as grandes formas; ruídos regionais e locais continuam presentes como detalhe, não como dados reais de relevo.
5. **Drenagem e erosão.** O D8/Priority-Flood descrito abaixo estima área contribuinte por patch. Essa área e a declividade afetam erosão/deposição numa única passagem; não há simulação temporal que recalcule rios depois de cada etapa erosiva.

O modelo puro está em `bedrock/STRATA_BehaviorPack/scripts/geology_model.js`; não depende da API do jogo e pode ser testado em Node. `main.js` registra a dimensão, gera chunks em fila e escreve colunas. A bússola `strata:geology_compass` leva à dimensão e, usada novamente nela, retorna ao spawn do Overworld.

### Auditoria da coerência tectônica

Para seed `12345`, a fração oceânica ponderada foi amostrada numa janela de 8.192×8.192 blocos, passo 256, e sua correlação de Pearson foi calculada a uma distância de 1.536 blocos. O resultado atual é **0,5529** no eixo X e **0,3359** no eixo Z. Na versão anterior (commit `3470950`), nas mesmas coordenadas e método, era **0,0837** e **−0,0376**. Isso demonstra maior continuidade espacial da classificação de crosta no modelo; não valida o formato dos continentes contra a Terra.

## Hidrologia e execução

Cada nó hidrológico cobre 8 blocos. O tile de saída contém 64×64 nós (**512×512 blocos**) e halo de 12 nós (**96 blocos**) por lado, totalizando patch de 88×88 nós (**704×704 blocos**). Priority-Flood usa as bordas do patch e células abaixo do nível do mar (`SEA_LEVEL=63`) como saídas; cede controle ao jogo durante o flood-fill. A direção D8 escolhe o vizinho mais baixo entre oito com desempate determinístico. Cada célula terrestre contribui uma unidade; a acumulação é propagada em ordem topológica, sem limite fixo de passos por caminho. O resultado é local, não representa bacias que atravessam o mundo.

A coluna final combina altura tectônica, erosão de encosta/fluvial, deposição e corte de canal, limitada a `[2,248]`. O modelo tem amostrador síncrono para testes/auditoria e assíncrono para o jogo. A igualdade bit a bit entre os dois caminhos não prova que o custo de ticks seja aceitável em todos os dispositivos.

## Resultados medidos

Auditoria reproduzível: seed `12345`; janela local `[-1024,1024)` em X e Z; passo 8 (**65.536** colunas); mar em Y=63. Os números são saídas do modelo, não metas universais nem terreno renderizado no Minecraft.

| Medida | Resultado observado |
|---|---:|
| Altura final min / P10 / P25 / mediana / P75 / P90 / máx | 26 / 34 / 37 / 46 / 63 / 76 / 111 blocos |
| Altura final média | 50,747 blocos |
| Altura-base min / média / máx | 27 / 51,3043 / 112 blocos |
| Colunas oceânicas finais (`height < 63`) | 74,321% |
| Máscara de rios (`river01 > 0,45`) | 2,8549% |
| Máscara de lagos (`lake01 > 0,45`) | 0,0092% |
| Declividade média / P95 | 0,0960 / 0,2486 |
| Fração oceânica ponderada da crosta na janela local | 0,6268 |
| Regime de margem forte (`regime01 > 0,5`): convergente / divergente / transformante | 8,1314% / 3,8071% / 24,5224% |

A calibração regional da altura-base, na janela de 8.192×8.192 blocos com passo 32 (**65.536** pontos), resultou em **71,6522%** abaixo do nível do mar. Isso é uma amostra plana da seed `12345`, não uma média global. Uma verificação adicional em janelas de 8.192 blocos com passo 64 deu **71,7041%** para seed `12345`, **53,8086%** para `54321`, **76,2329%** para `77777` e **69,9219%** para `1`; a variação por seed/janela reforça que a calibração não garante uma proporção local fixa.

Na comparação base→final das mesmas coordenadas atuais, 29,3381% das alturas mudaram; delta absoluto médio **0,5573** bloco, média assinada **−0,5573** e máximo absoluto **15** blocos. Isso mede erosão/deposição/canais dentro de uma execução, não a diferença entre versões.

### Comparação da revisão anterior com a atual

A revisão `1.1.1` (commit `3470950`) e a atual foram amostradas nas mesmas **65.536** coordenadas locais. A elevação-base mudou porque a classificação de crosta e os regimes tectônicos foram reformulados; esta é uma alteração grande do mapa, não apenas um ajuste hidrológico.

| Medida anterior → atual | Resultado |
|---|---:|
| Média da altura-base | 54,4725 → 51,3043 blocos |
| Colunas com altura-base diferente | 87,8677%; delta absoluto médio 11,5438, máximo 108 blocos |
| Média da altura final | 53,8257 → 50,7470 blocos |
| Colunas com altura final diferente | 87,7640%; delta absoluto médio 11,1718, máximo 106 blocos |
| Oceano final | 69,7723% → 74,3210% |
| Máscara de rios | 2,9816% → 2,8549%; 4,6036% dos pontos trocaram de classe |
| Máscara de lagos | 0,1541% → 0,0092% |

O ganho diretamente medido é a maior autocorrelação da fração de crosta na escala de placa, não que a topografia esteja fisicamente validada. A forte mudança na máscara de lagos é uma distribuição observada desta janela e merece validação visual; não foi mascarada nem tratada como alvo ideal.

### Emendas hidrológicas

Na mesma auditoria, pares vizinhos estão separados por 8 blocos. Para as emendas dos tiles de 512 blocos, a troca da máscara de rio foi **3,9063%** no eixo X e **0,9115%** no eixo Z; nos pares interiores, **2,6910%** em ambos os eixos. A diferença absoluta média de confluência foi 0,0231 / 0,0134 nas emendas X/Z e 0,0211 / 0,0207 no interior. O resultado varia por eixo e continua a mostrar efeitos de borda; uma janela e uma seed não provam continuidade em toda a dimensão.

## Como reproduzir

Na raiz do repositório, com Node.js moderno e Python 3:

```bash
node --check bedrock/STRATA_BehaviorPack/scripts/geology_model.js
node --check bedrock/STRATA_BehaviorPack/scripts/main.js
node --experimental-default-type=module --test tools/test_bedrock_geology.mjs
node --experimental-default-type=module tools/audit_bedrock_geology.mjs
bash tools/check_bedrock_types.sh  # declarações oficiais @minecraft/server 2.9.0
python3 tools/validate_bedrock_addon.py
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

Os oito testes verificam: determinismo seed/coordenadas; diferença entre seeds; sinais de convergência/divergência/cisalhamento conforme movimento relativo; autocorrelação da crosta na escala de placa; hidrologia determinística através de emendas; igualdade síncrona/assíncrona; limites e camadas; saltos entre chunks/tiles. O limite de altura adjacente do teste é 12 blocos e não prova continuidade universal. A validação Python é estrutural/estática, não substitui o jogo.

## Limitações ainda importantes

- **Não é física geológica simulada no tempo.** O modelo segue relações tectônicas plausíveis, mas as posições, tipos e velocidades das placas ainda são campos sintéticos derivados da seed. Não há geometria esférica, limites reais, convecção do manto, conservação de massa, espessura/idade crustal calibrada, isostasia nem evolução tectônica temporal. Ruído gradiente continua no campo; maior coerência não significa ausência de aleatoriedade procedural.
- **Hidrologia simplificada.** Uma unidade de escoamento por célula terrestre num patch de 704×704 blocos; não há precipitação calibrada, evaporação, infiltração, aquífero, vazão observada, transporte sedimentar, clima por latitude, bacia global nem reexecução da drenagem após erosão. Bordas de patch podem atuar como saídas artificiais.
- **Conteúdo do mundo.** A dimensão não recebe geração vanilla de árvores, cavernas, estruturas, biomas ou decoração. O Overworld conserva o relevo do Bedrock. Chunks já gerados mantêm seu marcador ao atualizar o pack; use um mundo de teste novo para ver a versão nova.
- **API e jogo.** A dependência estática é `@minecraft/server` 2.9.0, mas ainda falta importar o pacote e validar geração, dimensão, teleporte, bússola, custo de ticks e aparência no Minecraft Bedrock 1.26.52. O tutorial oficial de dimensões ainda pede Beta APIs; conferir o Content Log e testar em mundo descartável é necessário.

Para instalação e teste do add-on e para biomas do Overworld, consulte [`BEDROCK_PORT.md`](BEDROCK_PORT.md). A auditoria Forge permanece em [`GEOGRAPHIC_AUDIT.md`](GEOGRAPHIC_AUDIT.md).
