# Contrat de synchronisation implicite — `free_reg` / `wake_up`
 
## Le principe
 
`free_reg(i)` et `wake_up(i)` ne sont pas des événements ponctuels : ce sont des
niveaux, au sens d'un `valid` dans un couple valid/ack. Le scheduler les maintient
hauts aussi longtemps que la condition qu'ils représentent (`is_releasable`,
`is_dependency_resolved`) reste vraie — sans temporisation, sans compteur, sans
détection de front. Il se contente de refléter, cycle après cycle, l'état courant des
entrées qu'on lui donne (`bitmap`, compteurs de lecteurs, bits de mapping).
 
Ça fonctionne parce que ces entrées sont elles-mêmes de l'état persistant, porté par
d'autres modules (scoreboard, physical regs). Le scheduler n'a donc rien à mémoriser : la
seule question qu'il pose à chaque cycle est *« la condition est-elle vraie
maintenant ? »*.
 
## Ce qui met fin à la synchronisation
 
La boucle se referme côté récepteur, pas côté scheduler. Quand un module externe agit
sur ce que `free_reg`/`wake_up` lui signale (le free-list retire le tag du pool, le
scoreboard réalloue le registre), il met à jour **son propre état** — typiquement en
écrivant `bitmap(i) := false.B` dans son registre. Cette écriture est l'acquittement
(ack). Elle n'a pas besoin d'être un signal séparé : c'est la modification de l'état
qui, relue par le scheduler au cycle suivant, fait retomber la condition et donc le
niveau d'elle-même.
 
```
condition vraie → valid haut (free-reg) → traitement côté récepteur → predicat scheduler tombe → condition relue fausse au cycle suivant → valid retombe (free_reg)
```
 
## La règle non négociable côté récepteur
 
L'ack doit modifier l'entrée **D** d'une bascule, jamais la sortie combinatoire d'un
module, sinon cela crée une boucle combinatoire et des oscilations infinies
 
Forme correcte, côté module récepteur :
 
```scala
val bitmapReg = RegInit(VecInit(Seq.fill(n)(false.B)))
 
when (io.freeReg(i)) {
  bitmapReg(i) := false.B          // acquittement
} .elsewhen (producerDone(i)) {
  bitmapReg(i) := true.B
}
 
io.bitmap := bitmapReg             // le scheduler ne lit qu'une valeur deja stable
```