## `announced_reader_counter` et `served_reader_counter`

Ces deux compteurs servent à garantir qu’un registre physique n’est pas libéré tant que tous les consommateurs dans la issue queue qui en dépendent n’ont pas effectivement lu sa valeur.

* **`announced_reader_counter`** est incrémenté lorsqu’un nouveau lecteur d’un registre physique est identifié lors du renommage. Il représente donc le nombre total de lectures attendues pour ce registre.
* **`served_reader_counter`** est incrémenté lorsqu’un Worker effectue réellement la lecture du registre physique.

Un registre ne peut être considéré comme n’ayant plus de lecteurs en attente que lorsque :

```text
announced_reader_counter == served_reader_counter
```

Cette mécanique évite notamment un **use-after-free** : sans ces compteurs, un registre pourrait être remis dans la free list alors qu’une instruction dépendante a déjà été réveillée mais n’a pas encore atteint l’étage où elle lit réellement ses opérandes.

### Séquence typique en cas de RAW

Lorsqu’une instruction produit une valeur utilisée par une instruction ultérieure :

1. l’instruction productrice est en cours d’exécution et la dépendance RAW est enregistrée ;
2. lors du renommage du consommateur, `announced_reader_counter` est incrémenté et la dépendance est enregistrée dans l’Issue Queue ;
3. l'allocation est "agressive": une instruction en issue marque tout de même la dépendance mémoire de son registre destinataire. Une optimisation plus fine pourra être étudiée dans un second temps.
4. lorsque la donnée devient valide, le consommateur peut être réveillé ;
5. lorsqu’il lit effectivement le registre physique, `served_reader_counter` est incrémenté ;
6. lorsque tous les lecteurs annoncés ont été servis, les deux compteurs redeviennent égaux.

### Pourquoi `is_data_valid` reste nécessaire

Les compteurs de lecteurs ne remplacent pas `is_data_valid`.

Ils décrivent uniquement l’état des **consommateurs** : combien de lectures sont attendues et combien ont déjà été effectuées. `is_data_valid`, lui, décrit l’état du **producteur** : indique si la valeur a effectivement été produite et écrite dans le registre physique.

Par exemple, il est possible d’avoir :

```text
announced_reader_counter == 0
served_reader_counter    == 0
is_mapping_overwritten   == 1
is_data_valid            == 0
```

L’égalité des compteurs ne signifie alors pas que le registre peut être libéré : elle signifie uniquement qu’aucun lecteur n’est attendu. Le Worker producteur peut encore devoir écrire sa valeur.

Libérer le registre dans cet état permettrait sa réallocation avant cette écriture tardive, ce qui pourrait corrompre une autre valeur.

Le prédicat de libération doit donc vérifier indépendamment :

```text
is_data_valid
&& is_mapping_overwritten
&& (announced_reader_counter == served_reader_counter)
```

Ces trois informations couvrent trois propriétés distinctes : **production terminée**, **mapping architectural remplacé**, et **tous les consommateurs servis**.

---

# Role branch_predictor

## Adaptation dynamique de la profondeur de spéculation à partir des erreurs du prédicteur de branchement

### Principe général

L’idée consiste à conserver un **prédicteur de branchement Gshare classique avec historique** pour prédire la direction des branches, tout en ajoutant un second mécanisme indépendant chargé d’adapter dynamiquement la **profondeur maximale de spéculation** du processeur\.

Le Gshare continue donc de fonctionner normalement à partir du PC et de l’historique global des branches :

```text
Gshare :
GHR = T NT T T NT ...
```

où `T` signifie *Taken* et `NT` signifie *Not Taken*\.

En parallèle, un second historique mémorise non pas le résultat des branches, mais la **qualité des prédictions effectuées** :

```text
Contrôleur de profondeur :
history = C C M M C ...
```

avec :

- `C` = prédiction correcte \(*Correct*\)
- `M` = mauvaise prédiction \(*Mispredict*\)

Ces deux historiques ont donc des rôles différents\. Le premier sert à **prédire les branches**, tandis que le second sert à déterminer **à quel point le processeur peut se permettre de spéculer agressivement**\.

### Profondeur de spéculation

On définit une variable:

```text
max_authorized_branch_depth
```

qui représente le nombre maximal de branches non résolues sous lesquelles le processeur est autorisé à continuer à spéculer\.

Une valeur élevée permet d’aller chercher et d’exécuter davantage d’instructions en avance :

```text
B1
 └── B2
      └── B3
           └── B4
                └── instructions spéculatives
```

Cela peut augmenter les performances lorsque les prédictions sont bonnes\.

En revanche, si une branche ancienne est mal prédite, toutes les instructions situées sur le mauvais chemin doivent être annulées\. Une profondeur de spéculation élevée augmente alors la quantité de travail inutile\.

L’objectif du contrôleur est donc d’adapter automatiquement cette profondeur à la qualité récente du prédicteur\.

### Prise en compte des erreurs consécutives

Le mécanisme proposé ne considère pas toutes les erreurs de la même manière\.

Une mauvaise prédiction isolée peut être accidentelle et ne signifie pas nécessairement que le prédicteur traverse une mauvaise phase\. Elle entraîne donc une pénalité relativement faible\.

En revanche, plusieurs mauvaises prédictions consécutives indiquent davantage que la qualité actuelle des prédictions est mauvaise\. Elles doivent donc entraîner une diminution plus importante de l’agressivité spéculative\.

Une politique simple peut par exemple être :

```text
Résultat précédent   Résultat actuel   Variation du score
----------------------------------------------------------
C                    C                 +1
C                    M                 -1
M                    C                 +1 ou 0
M                    M                 -2
```

Ainsi, la séquence :

```text
C C C M C C 
```

ne provoque qu’une faible réaction, car la mauvaise prédiction semble isolée\.

En revanche :

```text
C C C M M M
```

entraîne une chute beaucoup plus rapide du score\.

On peut même généraliser la pénalité en fonction du nombre de mauvaises prédictions consécutives :

```text
1er miss consécutif  → -1
2e miss consécutif   → -2
3e miss consécutif   → -3
...
```

avec éventuellement une saturation afin d’éviter des variations trop importantes\.

### Exemple

Supposons une profondeur maximale initiale de :

```text
max_authorized_branch_depth = 6
```

et la séquence suivante :

```text
C  C  C  M  M  M
```

Le score pourrait évoluer ainsi :

```text
C → +1
C → +1
C → +1
M → -1
M → -2
M → -3
```

La première erreur n’entraîne donc qu’une petite réduction de confiance\. En revanche, les erreurs suivantes sont de plus en plus pénalisées\.

Lorsque le score descend sous certains seuils, la profondeur de spéculation est diminuée :

```text
bonne phase :
max_authorized_branch_depth = 8

quelques erreurs :
max_authorized_branch_depth = 6

mauvaise phase :
max_authorized_branch_depth = 3
```

À l’inverse, lorsque plusieurs prédictions correctes se succèdent, le score remonte progressivement et le processeur peut de nouveau augmenter sa profondeur de spéculation\.

### Intuition du mécanisme

L’objectif n’est donc pas seulement d’estimer le **taux moyen de bonnes prédictions**, mais également de détecter le régime actuel du prédicteur\.

Deux séquences peuvent par exemple avoir le même taux de réussite :

```text
C M C M C M C M
```

et :

```text
C C C C M M M M
```

Pourtant, la seconde séquence indique clairement qu’une mauvaise phase vient de commencer\.

Un compteur classique basé uniquement sur le nombre total de succès et d’échecs pourrait considérer les deux situations comme équivalentes\. Le mécanisme proposé réagit au contraire plus fortement à la seconde, car les erreurs sont regroupées\.

Il cherche donc implicitement à détecter les **rafales de mauvaises prédictions**\.

### Architecture proposée

L’architecture générale peut être représentée ainsi :

```text
          PC + GHR
             │
             ▼
      ┌─────────────┐
      │   Gshare    │
      └──────┬──────┘
             │
       prédiction T/NT
             │
             ▼
        Pipeline OoO
             │
        résolution branche
             │
             ▼
       Correct / Miss
             │
             ▼
 ┌────────────────────────┐
 │ Contrôleur de qualité  │
 │                        │
 │ historique C/M         │
 │ score                  │
 │ compteur de miss       │
 │ consécutifs            │
 └───────────┬────────────┘
             │
             ▼
  max_authorized_branch_depth
```

Le Gshare n’est donc pas modifié\. Le nouveau mécanisme agit uniquement sur **l’agressivité de la spéculation**\.

### Résumé

L’idée consiste à associer à un Gshare classique un contrôleur dynamique de profondeur de spéculation\. Ce contrôleur observe les prédictions correctes et incorrectes et adapte la profondeur maximale autorisée\.

La particularité principale est que les mauvaises prédictions ne sont pas toutes pénalisées de manière identique : **plusieurs mispredictions consécutives entraînent une pénalité croissante**\. Cela permet de distinguer une erreur isolée d’une véritable période de mauvaise prédictibilité\.

Le processeur peut ainsi spéculer profondément lorsque le prédicteur fonctionne bien et devenir rapidement plus conservateur lorsqu’une série de mauvaises prédictions apparaît\.
