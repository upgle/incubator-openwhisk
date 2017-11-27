FROM openwhisk/nodejs6action:1.9.0

RUN apt-get update
RUN apt-get install -y python-dev gcc python-pip
RUN pip install requests==2.12.5
RUN pip install python-glanceclient==2.6.0
RUN pip install python-openstackclient==3.8.1
