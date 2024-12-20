
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
const uuid = "42c0ffee-1337-7331-babe-42c0ffeebabe"
let curr = 0;
const nextMerkelapp = () => merkelapper[curr++ % merkelapper.length]

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
      return nextMerkelapp()
    }
    if (arg.name === "merkelapp") {
      return nextMerkelapp()
    }
    if (arg.name === "grupperingsid") {
      return uuid
    }
    if (arg.name === "eksternId") {
      return uuid
    }
    if (arg.name === "after") {
      return btoa(uuid)
    }
    if (arg.name === "virksomhetsnummer") {
        return "123456789"
    }
  }
}