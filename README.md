# University of Nottingham BSc Computer Science Dissertation Topic - Fall Detection using Machine Learning and IoT Devices

## FallGuardian

<img src="Images/FallGuardian%20Logo%20Transparent.png" alt="FallGuardian Logo" width="200"/>

Welcome to FallGuardian for Android, created by Alexandros Michaelides.
This repository showcases the work done as part of the dissertation project "Fall Detection using Machine Learning and IoT Devices". The project was supervised by Nazia Hameed and co-supervised by Adam Walker at the University of Nottingham.

The app sends the sensor data from the connected Wear OS smartwatch to a server with a machine learning algorithm to be analysed, and if a fall is detected, it automatically informs the emergency contact that is provided via SMS.

The app only collects the necessary sensor data and the IP address of the device being used to operate the app and neither of the two are stored anywhere. The emergency contact provided is only stored locally on the device you are using. No other personal information is stored.

The traditional machine learning models evaluated in this project include the k-Nearest Neighbours, SVM with a linear and a Radial Basis Function kernel, Random Forest and Naïve Bayes models. A deep learning method is also evaluated in the form of a convolutional neural network.

The publicly available datasets, SmartFall and UP-Fall, are evaluated using machine learning models which power fall detection system behind FallGuardian.

### Implementation Diagram:

<img src="Images/Implementation Diagram.png" alt="Implementation Diagram" width="400"/>

Sensor data from the smartwatch is transmitted to the connected smartphone every 5 seconds. The smartphone with the FallGuardian app serves as a relay, forwarding the sensor information to the server. The server extracts features from the data and utilises the proposed machine learning model to classify whether the event provided from the sensors was a fall. If classified as a fall, the server sends an acknowledgement back to the smartphone, triggering the 'Fall Detected' screen and a 2-second vibration on the smart-watch to alert the user. If the user indicates they need help or fails to respond within 15 seconds, the smartphone automatically sends an SMS to the designated emergency contact notifying them of the fall.

### Screenshots:

<img src="Images/FallGuardian%20Home.jpg" alt="FallGuardian Home" width="200"/>
<img src="Images/FallGuardian%20Settings.jpg" alt="FallGuardian Settings" width="200"/>

<img src="Images/FallGuardian%20About.jpg" alt="FallGuardian About" width="200"/>
<img src="Images/Smartwatch%20Alert.png" alt="Smartwatch Alert" width="200"/>

### Contents of Repository:

- Datasets obtained from SmartFall and UP-Fall
- FallGuardian Android App and Wear OS App source code
- Experiment Notebooks
  - Experiment 1 - SmartFall Undersampled
  - Experiment 2 - SmartFall SMOTE
  - Experiment 3 - UP-Fall Undersampled
  - Experiment 4- UP-Fall SMOTE
- ML Models
- Server Python files
  - Server_SmartFall_DL
  - Server_SmartFall_ML
  - Server_UPFall_DL
  - Server_SmartFall_ML

### Requirements to run Notebooks:

- Pandas
- NumPy
- Scikit-Learn
- TensorFlow
- Matplotlib
- Seaborn
- Joblib
- Imbalanced-Learn
