import android.content.Context
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient as PahoMqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

class MqttClient(context: Context, brokerUrl: String, clientId: String) {

    private val client: PahoMqttClient = PahoMqttClient(brokerUrl, clientId, null)

    fun connect(topic: String, onMessageReceived: (String) -> Unit) {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
        }

        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                // Handle connection lost
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                onMessageReceived(String(message.payload))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Handle delivery complete
            }
        })

        client.connect(options)
        client.subscribe(topic, 1)
    }

    fun disconnect() {
        if (client.isConnected) {
            client.disconnect()
        }
    }
}
