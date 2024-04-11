import { useState } from "react";
import { Pod, getPods, executeCommand } from "../api/KubernetesApi";
import Button from "../components/Button";
import useWebSocket from "react-use-websocket";

export default function KubernetesView() {
  const [socketUrl, setSocketUrl] = useState("ws://localhost:8080/shell");

  const { sendMessage, lastMessage, readyState } = useWebSocket(socketUrl);

  const handleClick = (requestId: string) => {
    setSocketUrl("ws://localhost:8080/shell?requestId=" + requestId);
    sendMessage("ls -l");
  };

  const [pods, setPods] = useState<Pod[]>([]);
  const onClick = () => {
    getPods()
      .then((response) => {
        setPods(response.pods);
      })
      .catch((error) => {
        console.error(error);
      });
  };

  return (
    <div>
      <Button onClick={onClick} className="m-auto w-full">
        GetPods
      </Button>

      <div>
        {pods.map((pod) => (
          <div key={pod.name} className="flex">
            <div>{pod.name}</div>
            <div>{pod.namespace}</div>
            <div>{pod.status}</div>
            <Button
              onClick={() => {
                executeCommand({
                  namespace: pod.namespace,
                  podName: pod.name,
                  command: "sleep 1 && echo bla && sleep 10 && echo world",
                })
                  .then((response) => {
                    console.log(response);
                  })
                  .catch((error) => {
                    console.error(error);
                  });
              }}
            >
              Execute command
            </Button>
            <div>
              <Button onClick={() => handleClick(pod.name)}>
                Send Message
              </Button>
              {lastMessage ? <div>Last message: {lastMessage.data}</div> : null}
              <div>Connection state: {readyState}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
