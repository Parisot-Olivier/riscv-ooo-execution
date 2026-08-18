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
