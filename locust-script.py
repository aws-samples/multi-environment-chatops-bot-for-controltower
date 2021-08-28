from locust import TaskSet, task, HttpUser, between
from string import ascii_lowercase
import secrets
import json

class ConverterTasks(TaskSet):

    @task
    def day_to_hour(self):
        length = 8;
        letters = ascii_lowercase
        result_str = ''.join(secrets.choice(letters) for i in range(length))
        name = result_str
        domain = '@amazon.com'
        payload = {'UserEmail': name+domain,'UserLastname':'Doe','UserName': name,'UserInput': 'Hi there'}
        headers = {'Content-Type': 'application/json'}
        self.client.post('/', data=json.dumps(payload), headers=headers)
# Receives the URL from CloudFormation output variable containing "/" as last character so omit it as first char

# Add tests to more resources as you require
#    @task
#    def day_to_minute(self):
#        self.client.get('items/1')

class ApiUser(HttpUser):
    tasks = [ConverterTasks]
    wait_time = between(1,5)
