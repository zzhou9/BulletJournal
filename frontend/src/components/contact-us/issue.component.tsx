import React from "react";
import {Button, Form, Input} from "antd";
import {contactSupport} from "../../apis/systemApis";
import {ContactType} from "../../features/system/constants";

type FeedbackProps = {};

const handleIssue = (title: String, content: String) => {
    contactSupport(ContactType.ISSUE, title, content).then((res) => {
        window.location.href = res.headers.get('Location')!;
    });
};

const Issue: React.FC<FeedbackProps> = () => {
    const [form] = Form.useForm();
    const onReset = () => {
        form.resetFields();
    };
    const onSubmit = () => {
        form
            .validateFields()
            .then((values) => {
                handleIssue(values.title, values.content);
            })
            .catch((info) => console.log(info));
    };

    const layout = {
        labelCol: { span: 4 },
        wrapperCol: { span: 16 },
    };
    const tailLayout = {
        wrapperCol: { offset: 4, span: 16 },
    };

    return <Form {...layout} form={form}>
        <Form.Item name="title" label="Title" rules={[{required: true, min: 3}]}>
            <Input/>
        </Form.Item>
        <Form.Item name="content" label="Content" rules={[{required: true, min: 3}]}>
            <Input.TextArea/>
        </Form.Item>
        <Form.Item {...tailLayout}>
            <Button type="primary" htmlType="submit" onClick={onSubmit}>
                Submit
            </Button>
            <Button htmlType="button" onClick={onReset}>
                Reset
            </Button>
        </Form.Item>
    </Form>
};

export default Issue;