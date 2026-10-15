# STRATA — Auditoria da coerência geográfica

**Escopo:** auditoria determinística do caminho de geração existente; sem
novos sistemas de relevo. Os números são medições, não metas de realismo.
Nenhum valor “ideal” foi presumido.

## 1. Caminho rastreado

1. `StrataChunkProvider.provideChunk(cx,cz)` converte índices locais em
   coordenadas absolutas de bloco: `wx=cx*16+localX`, `wz=cz*16+localZ`.
2. `StrataWorldModel.sampleColumn(wx,wz,out)` passa esses mesmos valores ao
   campo tectônico, elevação, hidrologia, clima, erosão, bioma e estratigrafia.
   As divisões por comprimento de onda são feitas dentro de cada modelo.
3. `PlateField.sample` combina o campo de continente com a classificação de
   movimento tectônico. `ElevationModel.sampleBase` mistura os ramos oceânico
   e continental, acrescenta relevo de placas, orogenia, rifte, escarpa e
   ruído local, depois aplica `reliefIntensity` em torno do nível do mar.
4. A altura final em blocos é `round(base.height - erosionLower +
   depositionRaise - riverCarve)`, limitada a `[2,maxSurfaceHeight]`.
   `ColumnData.baseHeight` é uma cópia arredondada da base contínua. A
   inclinação usa diferenças finitas de 2 blocos sobre a altura-base; clima e
   drenagem também partem da altura-base.
5. O topo sólido é escrito no índice de chunk 1.7.10
   `(localX*16+localZ)*256+y`. Se `finalHeight < seaLevel`, o provedor escreve
   água de `finalHeight+1` até o nível do mar; lagos e canais usam seus próprios
   topos de água acima da superfície.

Os modelos **não se anulam por sobrescrita**: a elevação é uma composição de
ramos misturados e termos aditivos; a etapa final subtrai erosão/corte de rio
e soma deposição. O problema comprovado era que uma classificação tectônica
rank-dependente podia zerar a máscara convergente de um bloco para o outro,
removendo o termo orogênico de forma abrupta.

### Coordenadas e escalas (configuração padrão)

| Campo | Escala/grade em blocos | Uso |
|---|---:|---|
| Placas | célula 1536; warp 320 de amplitude / 2800 de comprimento de onda | centros Voronoi e curvatura de fronteiras |
| Continentes | ruído 2400 | máscara `C`, independente das bordas de placas |
| Relevo regional/local | 600, 420 (abissal), 160 (cristas), 95 e 23 | modulação continental e detalhe |
| Margens tectônicas | orogenia 150/330; rifte 70; trincheira centrada em 50; escarpa 60 | perfis medidos em blocos |
| Hidrologia | célula 8; tile interno 16 células = 128 blocos; raio 12 células = 96 blocos | D8, confluência e bacias |
| Clima | temperatura 3000; umidade 2600 | campos regionais contínuos |
| Formação/estratos | província vulcânica 900; variantes 173; espessuras 200; sedimento 130; aluvião 90 | materiais e camadas |

Os comprimentos são diferentes intencionalmente; todos recebem as mesmas
coordenadas absolutas de bloco. A incompatibilidade encontrada foi de **fase**
da grade hidrológica: o tile amostrava no centro `(i+0.5)*cell`, enquanto a
interpolação antiga tratava `i*cell` como nó. A correção usa
`cellIndex = coordinate/cellSize - 0.5` nos dois eixos.

## 2. Causas-raiz comprovadas e correções

### A. Máscara convergente não era simétrica em uma troca F1/F2

A implementação anterior calculava o movimento por `(1,2)+(1,3)`. Quando os
centros mais próximos trocavam de ordem, o primeiro par continuava igual, mas
o par `(1,3)` mudava. Para `seed=12345`, uma amostra em `(-273,68)` e a coluna
vizinha `(-272,68)` mostravam, antes da correção:

- altura-base: **113.728 → 50.925 blocos** (diferença 62.803);
- `convergent01`: **0.9746 → 0.0000**;
- `C`: praticamente constante (**-0.3522 → -0.3523**).

A diferença não vinha do ruído nem de uma mudança de continente: o peso de
convergência desaparecia no rank swap, retirando a orogenia. A classificação
foi substituída por covariâncias ponderadas simétricas do stencil 3×3. Na
mesma coordenada, a altura-base atual é **74.0239 → 73.7124** (0.312 bloco),
`C=-0.31230 → -0.31233` e `convergent01=0.33327 → 0.32959`.

### B. Corte F1/F2/F3 deixava F4 entrar com peso finito

Os campos de bias/base anteriores usavam somente três placas, embora F3 ainda
tivesse peso diferente de zero. Ao trocar F3 por F4, o campo podia saltar. No
mesmo seed, em `(191,-216) → (192,-216)`, a versão original mudou altura-base
**53.585 → 38.880** e `C` **-0.6696 → -0.5890** em um bloco. Agora as
propriedades são ponderadas sobre os nove centros e os movimentos usam somas
simétricas. A altura-base nesse par é **50.6887 → 50.6665**, e `C` muda
**-0.55358 → -0.55403**.

### C. `sideW` era sempre não positivo

A fórmula anterior usava `q1=f1^4`, `q2=f2^4` e `(q1-q2)/(q1+q2)`. Como
`f1 <= f2` por definição, `sideW <= 0` em todo ponto. O código então escolhia
quase sempre o mesmo sinal em `scarpOffset`; o “degrau ±” descrito nos
comentários não existia. A correção usa uma média de bissetores orientados
por coordenadas estáveis dos centros e multiplica o valor assinado de forma
contínua (sem `sign(sideW)`). A regressão confirma a troca de sinal em uma
fronteira F1/F2 sem salto de altura. Uma tentativa intermediária de usar apenas
o par mais próximo foi rejeitada: ela criava saltos quando F2/F3 trocavam.

### D. Fase de amostragem da hidrologia deslocada em meia célula

`HydrologyModel.buildTile` amostra elevações nos centros da grade; `sample`
interpolava como se os valores estivessem nos cantos. Com célula padrão 8,
isso deslocava drenagem **4 blocos em X e 4 em Z**. A função
`blockToCellCenterIndex` agora é compartilhada com a amostragem e tem testes
para centros, meios de célula e coordenadas negativas.

Isolando somente essa correção, no mesmo seed/janela/coordenadas, com a nova
elevação mantida fixa: **13.463 de 262.144 colunas (5,14%)** mudaram a altura
final; `mean |ΔH|=0.1487`, máximo 13 blocos. A altura-base permaneceu idêntica.
A máscara de rio mudou em 3.734 colunas (1,42%); as diferenças mais altas
ocorrem quando a mudança de confluência cruza limiares de corte/água.

## 3. Medição espacial antes/depois

Configuração comum: `seed=12345`, `seaLevel=63`, janela de **2048×2048**
centrada em `(0,0)`, grade de amostragem a cada 4 blocos (**262.144** colunas).
Classes de inclinação são as já usadas pelo código: `<0.05`, `0.05..<0.35` e
`>=0.35`; “perto do mar” significa `abs(height-sea)<=2`. Isso descreve a
janela, não o mundo inteiro.

| Medida | Antes | Depois |
|---|---:|---:|
| Altura min / max / média | 33 / 128 / 49.8334 | 33 / 132 / 51.3226 |
| P10 / P25 / mediana / P75 / P90 | 39 / 42 / 46 / 53 / 69 | 41 / 43 / 47 / 54 / 72 |
| Abaixo de `sea-2` / perto do mar / acima de `sea+2` | 82.6565% / 3.7151% / 13.6284% | 81.6971% / 3.4672% / 14.8357% |
| Colunas oceânicas (`height<sea`) | 84.0408% | 83.1604% |
| Plano `<0.05` / encosta `0.05..<0.35` / íngreme `>=0.35` | 39.3330% / 56.3313% / 4.3358% | 40.1775% / 56.8485% / 2.9739% |
| Média da inclinação | 0.1117 | 0.0898 |
| Máscara continental `C>=0` | 11.2953%; 1 componente | 11.2793%; 1 componente |
| Média de `C` / corr(`C`, altura final) | -0.4015 / 0.7413 | -0.3879 / 0.7638 |
| Terra final `height>=sea` | 15.9592%; 34 comps.; maior 22.994 células | 16.8396%; 16 comps.; maior 28.313 células |
| Componentes de terra fechados na janela | 30 | 13 |
| Depressões `pit` | 0.7477%; maior comp. 252 células | 0.7446%; maior comp. 288 células |
| Lagos (`lake01>0.45`) | 0.2331%; 31 comps. | 0.1301%; 18 comps. |
| Orogenia forte (`orogen01>0.5`, `orogen>30` pré-escala) | 3.6407%; 15 comps. | 2.9427%; 7 comps. |

Na comparação ponto a ponto das mesmas 262.144 coordenadas, **152.039 colunas
(57,9983%)** mudaram a altura final (`mean |ΔH|=2.8003`, máximo 55 blocos);
150.827 alturas-base mudaram (`mean |Δbase|=2.7730`, máximo 59). Essa diferença
ampla é consequência da correção do campo tectônico, não de uma simples
mudança de água. “Células” usa passo 4; área aproximada = células × 16
blocos². Componentes fechados são apenas **ilhas-like dentro da janela**;
componentes podem tocar as bordas de amostragem e a conectividade depende do
passo.

### Continuidade nas bordas (pares adjacentes a 1 bloco)

| Amostra | Antes: média / P95 / máx. `|ΔH|` | Depois: média / P95 / máx. `|ΔH|` | Controles interiores depois |
|---|---:|---:|---:|
| Bordas de chunks (130.048 pares) | 0.0980 / 1 / 58 | 0.0965 / 1 / 8 | 0.0812 / 1 / 9 |
| Bordas hidrológicas (15.360 pares) | 0.0987 / 1 / 44 | 0.1054 / 1 / 6 | 0.0831 / 1 / 6 |

Antes da correção, o salto de 58 blocos na borda tinha uma diferença de
63 blocos na altura-base e um controle interior chegava a 61; portanto, não
era uma emenda exclusiva de chunks, mas uma descontinuidade tectônica real.
Depois, P95=1 nas bordas e nos controles; os máximos observados são da mesma
ordem de terreno íngreme amostrado dentro da janela. A média local continua
dependente da geologia da linha amostrada e não é, por si só, um teste de
costura.

### Janela regional maior

Também foi medida uma janela de **8192×8192**, passo 16 (262.144 amostras),
para observar estruturas que não cabem na janela local. Resultado atual:

- `C>=0`: **49.3675%**, 5 componentes conectados; os dois maiores têm
  84.387 e 38.952 células (áreas estimadas 21.603.072 e 9.971.712 blocos²).
- Terra final: **46.9086%**, 216 componentes; dois maiores com 64.450 e
  35.583 células; 208 componentes fechados na janela.
- `pit`: **1.0212%**, 852 componentes; `lake01>0.45`: **0.0439%**, 58
  componentes.
- Orogenia forte (`orogen01>0.5`, `orogen>30` pré-escala): **5.0625%**,
  44 componentes; dois
  maiores com 2.872 e 2.262 células (735.232 e 579.072 blocos² estimados).
- Altura min/máx/média: **24 / 196 / 63.6077**; oceano **53.0914%**;
  inclinação plana/encosta/íngreme **26.0506 / 68.3994 / 5.5500%**;
  `corr(C,finalHeight)=0.7432` nessa janela.

A amostragem de 16 blocos pode perder ilhas, canais e cristas estreitas; esses
números mostram a distribuição espacial nessa escala, não uma classificação
geológica global.

## 4. Arquivos e métodos alterados

- `core/tectonics/PlateField.java`: campos de placa, blending de propriedades,
  covariância de movimento e lado assinado dos bissetores.
- `core/tectonics/BoundarySample.java`: semântica documentada de `sideW` e
  dos campos do stencil.
- `core/terrain/ElevationModel.java`: uso contínuo do lado assinado no scarp;
  cálculo de relevo permanece na arquitetura existente.
- `core/hydrology/HydrologyModel.java`: conversão bloco→índice de célula-centro.
- `debug/TerrainAudit.java`, `tools/audit-terrain.sh`: histogramas,
  componentes, pares de borda e comparação CSV determinística.
- `test/HydrologyGridAlignmentTest.java`,
  `test/TectonicRankContinuityTest.java`, `test/TestRunner.java` e
  `test/PerfTest.java`: regressões e orçamento de performance atualizado.

## 5. Validação e limitações

A compilação/testes finais estão documentados em `TESTING.md` e
`VALIDATION.md`. Os testes core, os stubs Forge e os guardas de API não
substituem um link real contra Forge 10.13.4.1614 nem um teste visual em
Minecraft 1.7.10; esses ainda precisam ser executados em uma instalação com
JDK 8 e dependências Forge acessíveis.

Persistem limitações do modelo existente: confluência de raio limitado
(96 blocos), caminhadas D8 limitadas a 64 passos, sem feedback de erosão para
rerotear rios, sem rain shadows e sem simulação de evolução geológica ao
longo do tempo. As estatísticas não provam realismo avançado nem substituem a
inspeção visual. O resultado observado é mais estável nos rank swaps medidos;
a qualidade visual de falésias, bacias e continuidade das cadeias ainda exige
validação no jogo.
