(() => {
  const firebaseConfig = {
    apiKey: "AIzaSyCwp4d_vMD44UBK38WtVq7vF8CHT3QzA8c",
    authDomain: "indoone.firebaseapp.com",
    databaseURL: "https://indoone-default-rtdb.asia-southeast1.firebasedatabase.app/",
    projectId: "indoone",
    storageBucket: "indoone.firebasestorage.app",
    messagingSenderId: "715011875230",
    appId: "1:715011875230:web:4de5306bb94a0388e0b68f",
    measurementId: "G-RE8HGJ64GR"
  };

  if (!window.firebase) {
    console.error('Sankalp Firebase SDK is not loaded.');
    return;
  }

  const app = firebase.apps.length ? firebase.app() : firebase.initializeApp(firebaseConfig);
  const auth = firebase.auth();
  const database = firebase.database ? firebase.database() : null;
  // The app's CloudChatRepository writes the conversation to Firestore, so
  // the web has to reach the same place. Without this the chat had nowhere
  // to save and every write failed quietly.
  const firestore = firebase.firestore ? firebase.firestore() : null;

  // Start LOCAL persistence immediately and expose its promise so the app can
  // attach the auth-state listener only after Firebase finishes configuring it.
  const persistenceReady = auth?.setPersistence && firebase.auth?.Auth?.Persistence?.LOCAL
    ? auth.setPersistence(firebase.auth.Auth.Persistence.LOCAL).catch(error => {
        console.warn('Sankalp Firebase local persistence setup failed:', error);
      })
    : Promise.resolve();

  window.IndooneFirebase = {
    app,
    auth,
    database,
    firestore,
    fieldValue: firebase.firestore ? firebase.firestore.FieldValue : null,
    config: firebaseConfig,
    persistenceReady
  };
})();
