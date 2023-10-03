const LIFE_THE_UNIVERSE_AND_EVERYTHING = 42
const merkelapper = [
  "Tiltak",
  "Lønnstilskudd",
  "Mentor",
  "Sommerjobb",
  "Arbeidstrening",
  "Inkluderingstilskudd",
  "Dialogmøte",
  "Oppfølging",
  "Permittering",
  "Nedbemanning",
  "Innskrenking av arbeidstid",
  "Fritak arbeidsgiverperiode",
  "Inntektsmelding",
  "Lønnstilskudd",
  "Kandidater"
]
const uuid = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
  const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
  return v.toString(16);
})

// see: https://github.com/anvilco/spectaql/blob/main/examples/customizations/examples/index.js
module.exports = function processor({
                                      type,
                                      field,
                                      arg,
                                      inputField,
                                      underlyingType,
                                      isArray,
                                      itemsRequired,
                                    }) {
  console.log({
    type,
    field,
    arg,
    inputField,
    underlyingType,
    isArray,
    itemsRequired,
  })
  if (arg) {
    if (typeof arg.example !== 'undefined') {
      return
    }
    if (arg.name === "merkelapper") {
      return merkelapper.slice(0, Math.floor(Math.random() * merkelapper.length))
    }
    if (arg.name === "merkelapp") {
      return merkelapper[Math.floor(Math.random() * merkelapper.length)]
    }
    if (arg.name === "grupperingsid") {
      return uuid()
    }
    if (arg.name === "eksternId") {
      return uuid()
    }
    if (arg.name === "after") {
      return btoa(uuid())
    }
    if (arg.name === "virksomhetsnummer") {
      return Math.floor(Math.random() * 1000000000).toString().padStart(9, '0')
    }
  }
}