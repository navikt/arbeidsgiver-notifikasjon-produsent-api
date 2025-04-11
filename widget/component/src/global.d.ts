interface Window {
  notifikasjonWidgetUmami?: Umami
}

type Umami = {
    track: (eventName: string, eventData?: Record<string, any>) => void;
}
