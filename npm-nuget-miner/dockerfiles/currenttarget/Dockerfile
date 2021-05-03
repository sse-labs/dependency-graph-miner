FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j

COPY ./dependencygraphutils /usr/app/currenttarget/dependencygraphutils
COPY ./postprocessing/ /usr/app/currenttarget/postprocessing
COPY ./currenttarget.py /usr/app/currenttarget/currenttarget.py

WORKDIR /usr/app/currenttarget

ENTRYPOINT python /usr/app/currenttarget/currenttarget.py