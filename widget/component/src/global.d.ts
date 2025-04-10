interface Window {
  notifikasjonWidgetUmami?: {
    track: (eventName: string, eventData?: Record<string, any>) => void;
  };
}
