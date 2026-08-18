## Difficultés à venir

### Classement Fan-out

Plusieurs signaux risquent de devenir critiques en raison de leur fan-out et de leur présence sur des chemins de contrôle sensibles :

* **`mispredict`** : il s’agit probablement du point le plus critique, puisqu’un mispredict entraîne un arrêt immédiat de l’exécution en cours et un nettoyage de l’état spéculatif.
  Il faudra donc **pipeliniser la propagation de ce signal** et déclencher en priorité le reset des structures dont la remise à zéro est la plus coûteuse, notamment :

  * les bitmaps du scoreboard (car toute la bitmap n'est pas à remettre à 0, car les registres architecturax sont dans physical regs);
  * les structures d’allocation ;
  * la ROB.

* **`valid_data`** : à surveiller en raison de sa diffusion potentiellement importante dans le pipeline.

* **`bitmap`** : la taille et la distribution des bitmaps peuvent rapidement devenir problématiques, aussi bien en termes de fan-out que de routage.

* **`release_physical_reg`** : ce signal devra également être surveillé, notamment lorsqu’il est distribué vers plusieurs structures chargées du suivi ou de la réallocation des registres physiques.

### Nœud de routage on-chip

Le routage de **`physical_reg`** constitue également un point d’attention.

Le nombre de registres physiques restant relativement limité, il est acceptable que la synthèse utilise des **bistables (flip-flops)** plutôt qu’un véritable banc de SRAM pour avoir plusieurs port d'ecritures et de lectures (l'unicité de l'ecriture sur une case étant garantie par l'allocation des registres physiques justement pour enlever les fausses dependances WAR et WAW)

### Wake-up de l’Issue Queue

Dans une moindre mesure, la logique de **wake-up de l’Issue Queue** pourra également devenir un point critique.

Sa complexité dépendra notamment du nombre d’entrées de l’Issue Queue, du nombre de registres physiques suivis et de la quantité de comparaisons ou de dépendances devant être évaluées en parallèle.

## Coût en silicium

Une part importante du coût en silicium devrait provenir des différentes structures de stockage présentes dans l’architecture, en particulier :

* les différents niveaux de **cache** ;
* les différentes **FIFO** ;
* plus généralement, les structures nécessitant un nombre important d’entrées ou une forte réplication de leur logique de contrôle.

Pour davantage de détails concernant les choix d’implémentation, les contraintes associées et leur justification, voir [`contrats.md`](./contrats.md), [`roles.md`](./roles.md) et [`preuves.md`](./preuves.md).
