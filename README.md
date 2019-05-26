# GIS Application 2019
Based on GeoTools version 20-SNAPSHOT
---
# Part 3
Pasirinktoje teritorijoje surasti kalvas nuo kurių būtų galima įrengti slidinėjimo trasas pagal šias taisykles:

<ol>
<li>Vieta ieškoma interaktyviai stačiakampiu pažymėtoje teritorijoje ❌</li>
<li>Slidinėjimo trasos ilgis turi būti nemažesnis nei L1, o plotis nemažesnis nei L2, be to ji neturi būti tiesi ❌</li>
<li>Trasa negali kirsti kelių ir upių, bei turi nesiekti jų arčiau nei atstumu D1 ❌</li>
<li>Trasa negali eiti per vandenis, sodus ir urbanizuotas teritorijas, bei turi nesiekti jų arčiau nei atstumas D2 ❌</li>
<li>Vidutinis trasos nuolydis turi būti nemažesnis nei A1 ❌</li>
<li>Rezultatas yra surastos visos kalvos, bei nuo kalvos viršūnės suformuota ilgiausia galima trasa, tenkinanti sąlygas ❌</li>
<li>Taip paskaičiuoti suformuotos trasos ilgį ir kokia jos dalis eina mišku ❌</li>
</ol>

Teritoriją naudokite tą pačią kaip II užduotyje

---
# Part 2
Papildyti I užduoties programą funkcijomis, kurios leidžia pasirinkti bet kokį plotinį objektą (pagal RIBOS_P) iš anksčiau atrinktų administracinių vienetų ir sukurti funkcijas, leidžiančias suskaičiuoti:
<ol>
<li>hidrografijos teritorijų plotą, medžiais ir krūmais apaugusių teritorijų plotą, užstatytų teritorijų plotą, pramoninių sodų masyvų teritorijų plotą ir kiekvieno jų santykį su bendru atitinkamo administracinio vieneto plotu ✔</li>
<li>kiek ir kokio ploto statinių ir pastatų, hidrografijos teritorijos, medžiais ir krūmais apaugusių teritorijų, užstatytų teritorijų, pramoninių sodų masyvų teritorijų patenka į kultūros vertybių apsaugos zonas, pateikiant bendrus skaičius, bei kultūros vertybes, į kurias pateko išvardinti objektai ir kokia kiekvieno jų dalis ❌</li>
</ol>

---
# Part 1

# Full Requirements:
<ol>
  <li>Add/remove layer, show/hide layer ✔</li>
  <li>Zoom in/out ✔</li>
  <li>Pan ✔</li>
  <li>Full extent ✔</li>
  <li>Select features, multiselect ✔</li>
  <li>Zoom to select ✔</li>
  <li>Show data for selected objects, show objects for selected data ✔</li>
  <li>Show data table for selected layer ✔</li>
  <li>Select data by attributes and show the objects on the map ✔</li>
  <li>Select by location ✔</li>
  <li>Save selected objects to a new layer ✔</li>
</ol>

# Bugs
<ul>
  <li>Coordinates Reference System (CRS) is not always shown/detected. Gets updated only after resizing window</li>
</ul>
