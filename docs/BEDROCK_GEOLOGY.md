# STRATA Bedrock — modelo geológico e auditoria reproduzível

**Escopo:** algoritmo procedural de relevo para a dimensão opcional `strata:geology`, construída por Script API no Bedrock 1.26.52. Isso não altera o heightmap do Overworld; as oito biomas com `replace_biomes` continuam sendo uma implementação separada de materiais/clima no mundo vanilla. A dimensão é um caminho experimental de avaliação, não uma substituição aprovada para o Overworld.

## Referências que orientam o modelo

| Referência | Relação com a implementação | O que não se conclui dela |
|---|---|---|
| USGS, [Understanding plate motions](https://pubs.usgs.gov/gip/dynamic/understanding.html) | Distingue margens convergentes, divergentes e transformantes e associa subducção a fossas/arcos vulcânicos, divergência a riftes/cristas e colisão continental a soerguimento. Essas relações orientam os sinais e perfis do relevo sintético. | As placas do add-on não usam geometrias, velocidades ou limites observados da Terra. |
| NOAA, [The Ocean](https://globalocean.noaa.gov/the-ocean/) | Informa cobertura oceânica de cerca de 71% da superfície da Terra. Foi uma referência externa para a calibração regional de `CONTINENTAL_OFFSET`. | A janela plana 2D de uma seed não é a superfície esférica da Terra; a semelhança percentual não demonstra distribuição global realista. |
| O’Callaghan & Mark (1984), [extração de redes de drenagem em modelos digitais de elevação](https://doi.org/10.1016/S0734-189X(84)80011-0) | Referência clássica para direção de fluxo D8 e acumulação em grade. | O add-on usa uma grade pequena e limitada; não é um modelo hidrológico calibrado. |
| Barnes, Lehman & Mulla (2014), [Priority-Flood](https://doi.org/10.1016/j.cageo.2013.04.024) | Inspira o preenchimento de depressões usando uma fila de prioridade e células de saída. | A implementação é uma adaptação local, com as bordas do patch e células abaixo do nível do mar como saídas, e desempate determinístico. |
| Whipple & Tucker (1999), [stream-power river incision model](https://doi.org/10.1029/1999JB900120) | Motiva usar declividade e área de drenagem como sinais para erosão fluvial. | Os coeficientes, expoentes, limites e unidades do jogo são heurísticos; não foram ajustados a taxas físicas da Terra. |

## Arquitetura e processo

O modelo puro está em `bedrock/STRATA_BehaviorPack/scripts/geology_model.js`; não importa a API do jogo, portanto pode ser comparado e testado em Node. `main.js` registra a dimensão vazia, procura um ponto de entrada, gera chunks em fila e escreve as colunas com `Dimension.fillBlocks`. A bússola `strata:geology_compass` leva à dimensão e, usada novamente nela, retorna ao spawn do Overworld.

1. **Determinismo espacial.** O seed do mundo é convertido em um inteiro por hash; ruído gradiente, fBm e campos discretos usam hashes com salts fixos e coordenadas absolutas. Não há RNG global nem dependência da ordem de geração.
2. **Placas sintéticas.** Centros com jitter em células de 1.536 blocos formam um campo tipo Voronoi; um warp de 320 blocos / comprimento de onda 2.800 curva as margens. Cada célula recebe tipo oceânico/continental e velocidade determinística. Cerca de 70% das células são oceânicas. Movimentos relativos ponderados classificam convergência, divergência e cisalhamento.
3. **Elevação-base.** Um campo continental de escala 2.400 blocos e o bias das placas misturam fundos oceânicos e terras; ruído regional, relevo local, cristas, riftes, escarpas, fossas e orogenia modulam o resultado. Convergência gera perfis de montanha/fossa, divergência pode gerar crista oceânica ou vale de rifte e cisalhamento produz escarpa limitada. `CONTINENTAL_OFFSET = -0.40` foi escolhido após medir uma janela regional da seed fixa abaixo.
4. **Drenagem D8 por tile.** Cada nó cobre 8 blocos. O tile de saída é 64×64 nós (**512×512 blocos**), com halo de 12 nós (**96 blocos**) por lado; o patch calculado mede 88×88 nós (**704×704 blocos**). O Priority-Flood usa as bordas do patch e as células abaixo do nível do mar (`SEA_LEVEL=63`) como saídas e cede controle a cada 512 visitas. A direção D8 escolhe o vizinho mais baixo entre oito, com desempate determinístico. Cada célula terrestre contribui uma unidade de escoamento; a área contribuinte é propagada uma vez em ordem topológica, da maior para a menor elevação drenada, sem limite de passos por caminho. O resultado estima confluência, canais e depressões/lagos **dentro do patch**, não em uma bacia global.
5. **Erosão, deposição e estratos.** Área contribuinte aproximada e declividade reduzem a altura; litologia altera o fator de resistência. Erosão fluvial e de encosta tem tetos, deposição é limitada, e o corte de canal/lago ajusta o resultado. Em forma compacta, `final = round(base - erosão + deposição - corte_de_rio)`, limitada a `[2,248]`. A superfície, sedimentos e três camadas de rocha usam a formação determinística da coluna. Camadas e água são escritas depois do bedrock do piso.
6. **Execução cooperativa.** Existe amostrador síncrono para auditoria/teste e assíncrono para o jogo. A construção dos tiles de drenagem é um gerador que cede controle por linhas e durante o flood-fill e a acumulação; a escrita da coluna também é processada em lotes. A igualdade dos caminhos síncrono/assíncrono não prova que o tempo de geração seja aceitável em todos os dispositivos.

### Calibração oceânica (não é validação global)

Na seed `12345`, a altura-base foi amostrada em uma janela quadrada de 8.192×8.192 blocos, passo 32 (**65.536** pontos). **71,7194%** dos pontos ficaram abaixo do nível do mar depois de fixar o offset em `-0.40`, perto da referência NOAA de 71%. Essa medição é de um plano local, da altura-base e de uma seed; não representa a Terra, nem sequer uma média global do add-on.

## Resultados medidos

Auditoria reproduzível: seed `12345`, janela `[-1024,1024)` em X e Z, passo de 8 blocos (**65.536** colunas). Altura do mar: Y=63. Os valores abaixo são amostras do modelo, não metas de design nem resultados do Minecraft renderizado.

| Medida | Resultado observado após a mudança |
|---|---:|
| Altura final min / P10 / P25 / mediana / P75 / P90 / máx | 27 / 35 / 42 / 52 / 66 / 73 / 139 blocos |
| Altura final média | 53,8257 blocos |
| Altura-base min / média / máx | 27 / 54,4725 / 141 blocos |
| Colunas oceânicas finais (`height < 63`) | 69,7723% |
| Máscara de rios (`river01 > 0,45`) | 2,9816% |
| Máscara de lagos (`lake01 > 0,45`) | 0,1541% |
| Declividade média / P95 | 0,1126 / 0,3471 |

Na comparação base→final das mesmas coordenadas, 32,193% das alturas mudaram; delta absoluto médio **0,6468** bloco, média assinada **−0,6468** e máximo absoluto **18** blocos. Isso mede erosão/deposição/canais dentro de uma execução, não a mudança entre versões.

| Formação | Participação amostrada |
|---|---:|
| Aluvial | 0,3204% |
| Carbonática | 9,0851% |
| Bacia clástica | 13,2782% |
| Oceânica | 69,2856% |
| Orogênica | 2,3209% |
| Rifte | 3,8071% |
| Vulcânica | 1,9028% |

A participação de formação é uma classe interna do modelo, não um mapa geológico validado. `shield` não apareceu nessa janela local; a condição dessa formação pode ocorrer em outras regiões/seeds.

### Comparação antes/depois em coordenadas idênticas

A versão anterior e a versão atual foram amostradas nas mesmas **65.536** coordenadas da janela local. A elevação-base permaneceu idêntica em todos os pontos: a mudança ficou concentrada na drenagem e nos termos finais que dela dependem.

| Medida anterior→atual | Resultado |
|---|---:|
| Altura final que mudou | 0,6805% das colunas |
| Média absoluta / média assinada / máximo absoluto de `Δheight` | 0,0172 / −0,0126 / 10 blocos |
| Máscara de rios | 2,8183% → 2,9816%; 0,1755% dos pontos trocaram de classe |
| Máscara de lagos | 0,1373% → 0,1541%; 0,0168% dos pontos trocaram de classe |
| Formação | 0,0137% dos pontos trocaram de classe |

A alteração teve efeito pequeno no relevo amostrado e não deslocou a elevação tectônica. A área de drenagem calculada tornou-se mais consistente com uma acumulação D8 topológica; isso não significa que a forma ou descarga dos rios tenha sido validada.

### Emendas de tiles hidrológicos

Para seed `12345`, mediram-se pares vizinhos separados por 8 blocos na mesma janela. Antes, o tile de saída tinha 128 blocos; agora tem 512. O número de linhas internas de emenda amostradas caiu de 15 para 3 (3.840 para 768 pares por eixo). A métrica de transição não desapareceu: nas linhas reais de emenda atuais, a porcentagem de mudanças da máscara de rio é 2,9948% no eixo X e 3,1250% no eixo Z, contra 2,6987% e 2,9421% nos pares interiores. Nas emendas físicas comuns de 512 blocos, a porcentagem de mudança da máscara foi igual antes e depois (2,9948% em X; 3,1250% em Z). Assim, o resultado medido é **menos linhas de emenda**, não uma prova de que cada emenda ficou melhor.

A diferença absoluta média de confluência nas emendas atuais é 0,0218 (X) e 0,0237 (Z); nos pares interiores, 0,0222 e 0,0235. O teste cobre uma janela/seed/passo, não toda a dimensão.

## Como reproduzir

Na raiz do repositório, com Node.js moderno e Python 3:

```bash
node --check bedrock/STRATA_BehaviorPack/scripts/geology_model.js
node --check bedrock/STRATA_BehaviorPack/scripts/main.js
node --experimental-default-type=module --test tools/test_bedrock_geology.mjs
node --experimental-default-type=module tools/audit_bedrock_geology.mjs
bash tools/check_bedrock_types.sh  # npm: TypeScript + official @minecraft/server 2.9.0 declarations
python3 tools/validate_bedrock_addon.py
python3 tools/package_bedrock_addon.py \
  --output bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
python3 tools/validate_bedrock_addon.py \
  --archive bedrock/releases/STRATA-Geological-World-Engine-Bedrock-1.26.52.mcaddon
```

Os seis testes Node verificam repetibilidade da mesma seed/coordenadas, variação entre seeds, determinismo da hidrologia através de emenda de tile, igualdade bit a bit entre caminhos síncrono/assíncrono, limites e ordem das camadas, e saltos adjacentes em linhas que cruzam chunks/tiles. A tolerância do teste de altura é de no máximo 12 blocos; ele não prova continuidade em toda a dimensão. A validação Python é estática e verifica JSON, links de packs/itens/texturas/features e estrutura do arquivo importável.

## Limitações e validação ainda necessária

- **Não é uma simulação geofísica.** As placas são polígonos sintéticos, com tipos e velocidades aleatórios determinísticos; não usam mapa real, esfericidade, cinemática calibrada, conservação de massa, espessura crustal, isostasia ou ciclo tectônico temporal. O ruído/escala em blocos não equivale a quilômetros/tempo geológico.
- **Hidrologia ainda local e simplificada.** O D8 acumula uma unidade por célula terrestre dentro de um patch de 704×704 blocos; as bordas desse patch podem atuar como saídas artificiais. Não há chuva calibrada, vazão observada, evaporação, infiltração, aquíferos, transporte de sedimento, precipitação orográfica, clima por latitude, feedback da erosão para recalcular rios, nem bacias que atravessem a dimensão inteira. A mudança removeu o corte de 64 passos por caminho, mas não removeu o limite espacial do patch nem as emendas entre tiles.
- **Conteúdo de mundo.** A dimensão é vazia além do terreno escrito pelo script; não recebe a geração vanilla de árvores, cavernas, estruturas, biomas ou decoração. O Overworld conserva o relevo do Bedrock. Chunks já gerados mantêm seu marcador e não são regenerados ao atualizar o pack.
- **API/experimento.** O changelog do Bedrock 1.26.30 lista `DimensionRegistry` e `StartupBeforeEvent.dimensionRegistry` entre APIs estáveis; o tutorial atual de dimensões customizadas ainda classifica o recurso como experimental e pede o toggle **Beta APIs**. A dependência estável `@minecraft/server` 2.9.0 é verificada estaticamente, mas essa divergência documental torna obrigatório testar em um mundo descartável e consultar o Content Log. Se a dimensão não registrar, repetir o ensaio com o toggle indicado pelo jogo/documentação; não assumir que a validação offline prova suporte.
- **Sem teste no jogo até aqui.** O modelo passou testes Node e análise de tipos contra as declarações oficiais de `@minecraft/server` 2.9.0, mas ainda falta importar o `.mcaddon` e validar geração, teleporte, uso da bússola, retorno seguro, compatibilidade do registro da dimensão, custo de ticks, estabilidade e aparência em Minecraft Bedrock 1.26.52. A aceitação da dimensão separada como destino pretendido também permanece decisão do usuário.

Para o roteiro de instalação/teste do add-on e para a implementação de biomas do Overworld, consulte [`BEDROCK_PORT.md`](BEDROCK_PORT.md). A auditoria Forge, com sua comparação própria, permanece em [`GEOGRAPHIC_AUDIT.md`](GEOGRAPHIC_AUDIT.md).
