
const måndenavn = [
    'jan.', 'feb.', 'mars', 'apr.', 'mai', 'juni',
    'juli', 'aug.', 'sep.', 'okt.', 'nov.', 'des.'
];

export const formatterDato = (dato: Date) =>
    `${dato.getDate()}. ${måndenavn[dato.getMonth()]} ${dato.getFullYear()}`;

const today = new Date();
const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 1);
const tomorrow = new Date();
tomorrow.setDate(tomorrow.getDate() + 1);

const isToday = (date: Date): boolean =>
    formatterDato(date) === formatterDato(today);

const isYesterday = (date: Date): boolean =>
    formatterDato(date) === formatterDato(yesterday);

const isTomorrow = (date: Date): boolean =>
  formatterDato(date) === formatterDato(tomorrow);

const klokkeslett = (dato: Date) => {
    const hour = dato.getHours().toString().padStart(2, '0');
    const minute = dato.getMinutes().toString().padStart(2, '0');
    return hour + '.' + minute;
};

export const sendtDatotekst = (dato: Date) => {
  if (isToday(dato)) {
    return 'I dag ' + klokkeslett(dato);
  } else if (isYesterday(dato)) {
    return 'I går ' + klokkeslett(dato);
  } else {
    return formatterDato(dato);
  }
};

export const uformellDatotekst = (dato: Date) => {
    if (isToday(dato)) {
        return 'i dag';
    } else if (isYesterday(dato)) {
        return 'i går';
    } else if (isTomorrow(dato)) {
      return 'i morgen';
    } else {
        return formatterDato(dato);
    }
};
